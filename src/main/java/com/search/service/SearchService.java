package com.search.service;

import com.search.model.SearchResult;
import com.search.model.Lemma;
import com.search.model.Page;
import com.search.model.Site;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final LemmaScanner lemmaScanner;
    private final DBConnector fastDBConnector;
    private final Logger logger;

    public SearchService(LemmaScanner lemmaScanner, DBConnector fastDBConnector, Logger logger) throws IOException {
        this.lemmaScanner = lemmaScanner;
        this.fastDBConnector = fastDBConnector;
        this.logger = logger;
    }


    public List<SearchResult> search(String query) throws IOException, SQLException {
        Map<String, Integer> queryLemmsMap = lemmaScanner.scan(query);
        if (query.length() < 1) return new ArrayList<>();

        List<Lemma> queryLemmsList = queryLemmsMap.keySet()
                .stream()
                .map(ls -> new Lemma(0, ls, queryLemmsMap.get(ls)))
                .toList();
        Set<Page> pages = getPagesForQueryList(queryLemmsList);
        List<SearchResult> searchResults = getSearchResults(pages, queryLemmsList, queryLemmsMap);
        float maxRelevance = getMaxRelevance(searchResults);
        for (SearchResult searchResult : searchResults) {
            searchResult.setRelativeRel(searchResult.getAbsoluteRel() / maxRelevance);
        }
        searchResults.sort(Comparator.comparing(SearchResult::getRelativeRel).reversed());
        writeSearchResultsLogs(searchResults);
        return searchResults;
    }

    private void writeSearchResultsLogs(List<SearchResult> searchResults) {
        for (SearchResult searchResult : searchResults) {
            logger.debug(searchResult.getPage().getPath() + " - " +
                    searchResult.getRelativeRel() + " - " + searchResult.getAbsoluteRel());
        }
    }

    private float getMaxRelevance(List<SearchResult> searchResults) {
        float maxRelevance = 0.0F;
        for (SearchResult searchResult : searchResults) {
            if (searchResult.getAbsoluteRel() <= maxRelevance) continue;
            maxRelevance = searchResult.getAbsoluteRel();
        }
        return maxRelevance;
    }

    private List<SearchResult> getSearchResults(Set<Page> pages, List<Lemma> queryLemmsList, Map<String, Integer> queryLemmsMap) throws IOException {
        List<SearchResult> searchResults = new ArrayList<>();
        float maxRelevance = 0.0F;
        for (Page page : pages) {
            Map<String, Float> lemmsForPage = lemmaScanner.getLemmsForPageBySearchWords(page, queryLemmsList);
            float absoluteRel = 0.0F;
            for (String s : lemmsForPage.keySet()) {
                absoluteRel += lemmsForPage.get(s);
                if (absoluteRel > maxRelevance) {
                    maxRelevance = absoluteRel;
                }
            }
            SearchResult searchResult = new SearchResult(page, getSnippet(page, queryLemmsMap.keySet()), absoluteRel);
            if (!searchResult.getSnippet().isEmpty()) searchResults.add(searchResult);
        }
        return searchResults;
    }

    private Set<Page> getPagesForQueryList(List<Lemma> queryLemmsList) throws SQLException {
        Set<Page> pages = new HashSet<>(fastDBConnector.getPagesByLemma(queryLemmsList.get(0).getLemma()));

        if (queryLemmsList.size() >= 1) {
            for (int i = 1; i < queryLemmsList.size(); i++) {
                List<Page> pagesList = fastDBConnector.getPagesByLemma(queryLemmsList.get(i).getLemma());
                List<String> strings = pagesList.stream().map(Page::getPath).toList();
                pages = pages.stream().filter(p -> strings.contains(p.getPath())).collect(Collectors.toSet());
            }
        }
        return pages;
    }

    private String getSnippet(Page page, Set<String> queryLemmsSet) {
        String sourceText = Jsoup.parse(page.getContent()).text();
        String text = prepareSourceText(sourceText);
        List<String> words = Arrays.stream(text.split(" ")).toList();
        Map<Integer, String> indexes = getWordsIndexes(words, queryLemmsSet, sourceText);
        StringBuilder snippetBuilder = buildSnippet(indexes, sourceText);
        return cutSnippet(snippetBuilder);
    }

    private String cutSnippet(StringBuilder snippetBuilder) {
        String resultSnippet = "";
        for (int snippetStart = 0; snippetStart < snippetBuilder.length(); snippetStart++) {
            int snippetEnd = snippetStart + 200;
            if (snippetEnd > snippetBuilder.length()) {
                break;
            }
            String nextSnippet = snippetBuilder.substring(snippetStart, snippetEnd);
            String regex = "</b>";
            String[] parts = resultSnippet.split(regex);
            String[] nextParts = nextSnippet.split(regex);
            if (nextParts.length > parts.length) {
                resultSnippet = nextSnippet;
            }
        }
        return resultSnippet;
    }

    private StringBuilder buildSnippet(Map<Integer, String> indexes, String sourceText) {
        StringBuilder snippetBuilder = new StringBuilder();
        for (Integer index : indexes.keySet()) {
            if (!snippetBuilder.toString().contains(indexes.get(index))) {
                int start = index - 200;
                int end = index + 250;

                if (start < 0) {
                    start = 0;
                }

                if (end > sourceText.length()) {
                    end = sourceText.length() - 1;
                }
                String snippet = sourceText.substring(start, end);
                snippetBuilder.append(snippet).append("...");
            }
        }
        snippetBuilder.insert(0, "...");
        snippetBuilder = new StringBuilder(snippetBuilder.toString().toLowerCase());
        return highlightsKeywords(snippetBuilder, indexes);
    }

    private StringBuilder highlightsKeywords(StringBuilder snippetBuilder, Map<Integer, String> indexes) {
        for (String word : indexes.values()) {
            if (snippetBuilder.toString().contains(word)) {
                int firstWordIndex = snippetBuilder.indexOf(word);
                int lastWordIndex = snippetBuilder.lastIndexOf(word);

                if (firstWordIndex == lastWordIndex) {
                    snippetBuilder.insert(firstWordIndex, "<b>").insert(lastWordIndex + word.length() + 3, "</b>");
                }
                while (firstWordIndex < lastWordIndex) {
                    if (firstWordIndex < 0) {
                        break;
                    }
                    snippetBuilder.insert(firstWordIndex, "<b>").insert(firstWordIndex + word.length() + 3, "</b>");
                    firstWordIndex = snippetBuilder.indexOf(word, firstWordIndex + word.length() + 3);
                }
            }
        }
        return snippetBuilder;
    }

    private Map<Integer, String> getWordsIndexes(List<String> words, Set<String> queryLemmsSet, String sourceText) {
        Map<Integer, String> indexes = new HashMap<>();
        for (String word : words) {
            List<String> normalForms = lemmaScanner.getNormalForm(word);

            for (String normalForm : normalForms) {
                if (!queryLemmsSet.contains(normalForm)) continue;
                indexes.put(sourceText.indexOf(word), word);
            }
        }
        return indexes;
    }

    private String prepareSourceText(String sourceText) {
        String text = sourceText.replaceAll("[\\p{P}\\p{S}]", "").toLowerCase();
        text = text.replaceAll(" —", "");
        return text.replaceAll("\\s+", " ");
    }

    public JSONObject toJSONObject(List<SearchResult> searchResults) throws SQLException {

        JSONObject response = new JSONObject();
        response.put("result", true);
        response.put("count", searchResults.size());
        List<JSONObject> searchResultsList = new ArrayList<>();

        for (SearchResult searchResult : searchResults) {
            JSONObject searchResultObject = new JSONObject();

            Site site = fastDBConnector.getSiteById(searchResult.getPage().getSiteId());
            String siteUrl = site.getUrl();
            String title = Jsoup.parse(searchResult.getPage().getContent()).getElementsByTag("title").text();
            String uri = searchResult.getPage().getPath();
            int length = siteUrl.length();
            double relevance = Math.round(searchResult.getRelativeRel());
            double scale = Math.pow(10, 3);
            double result = Math.ceil(relevance * scale) / scale;

            searchResultObject.put("site", siteUrl);
            searchResultObject.put("siteName", site.getName());
            searchResultObject.put("uri", uri.substring(length + 1));
            searchResultObject.put("title", title);
            searchResultObject.put("snippet", searchResult.getSnippet());
            searchResultObject.put("relevance", result);
            searchResultsList.add(searchResultObject);
        }
        response.put("data", searchResultsList);

        return response;
    }

    public List<SearchResult> filterBySite(List<SearchResult> searchResults, String siteUrl) {
        List<SearchResult> filteredSR = new ArrayList<>();
        for (SearchResult searchResult : searchResults) {
            Page page = searchResult.getPage();
            String path = page.getPath();
            if (path.contains(siteUrl)) filteredSR.add(searchResult);
        }
        return filteredSR;
    }
}
