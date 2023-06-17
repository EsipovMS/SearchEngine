package com.search.service;

import com.search.model.Index;
import com.search.model.Lemma;
import com.search.model.Page;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class Storage {
    private final LemmaScanner lemmaScanner;
    private final DBConnector DBConnector;
    private final Logger logger;
    private boolean isStarted;

    private final AtomicInteger indexId = new AtomicInteger(0);
    private final AtomicInteger lemmaId = new AtomicInteger(0);
    private final AtomicInteger pageId = new AtomicInteger(0);
    private final Set<Page> pages = new CopyOnWriteArraySet<>();
    private final Map<String, List<Lemma>> lemms = new ConcurrentHashMap<>();
    private final List<Index> indexes = new CopyOnWriteArrayList<>();
    private final Set<String> usedLinks = new CopyOnWriteArraySet<>();
    private final AtomicInteger count = new AtomicInteger(0);
    private boolean stop = false;

    public Storage(LemmaScanner lemmaScanner, DBConnector DBConnector, Logger logger) {
        this.lemmaScanner = lemmaScanner;
        this.DBConnector = DBConnector;
        this.logger = logger;
    }

    public synchronized void addPage(Page page) throws IOException, SQLException {
        if(stop) return;
        pages.add(page);
        if (pages.size() % 50 == 0) {
            logger.info("Найдено " + pages.size() + " страниц");
        }
        if (pages.size() >= 500) {
            createLemmsAndIndexesAndCollect();
            DBConnector.saveAllLemms(lemms);
            clearLemmas();
            DBConnector.saveAllIndexes(indexes);
            clearIndexes();
            DBConnector.saveAllPages(pages);
            clearPages();
        }
    }

    public synchronized void saveAllData() throws IOException, SQLException {
        createLemmsAndIndexesAndCollect();
        if (lemms.size() > 0) {
            DBConnector.saveAllLemms(lemms);
            clearLemmas();
        }
        if (indexes.size() > 0) {
            DBConnector.saveAllIndexes(indexes);
            clearIndexes();
        }
        if (pages.size() > 0) {
            DBConnector.saveAllPages(pages);
            clearPages();
        }
    }

    public synchronized void createLemmsAndIndexesAndCollect() throws IOException, SQLException {
        if (lemms.size() != 0) DBConnector.saveAllLemms(lemms);
        lemms.clear();
        Set<Page> pages = new HashSet<>(getPages());
        for (Page page : pages) {
            count.incrementAndGet();
            String path = page.getPath();
            path = formatPath(path);
            if (count.get() % 50 == 0) {
                logger.info(String.format("Идет создание лемм и индексов (%s из %s)", count.get(), pages.size()));
            }
            String message = String.format("Идет создание лемм и индексов: %s (%d из %d)", path, count.get(), pages.size());
            logger.debug(message);
            int siteId = page.getSiteId();
            String textBody = Jsoup.parse(page.getContent()).text();
            String textTitle = Jsoup.parse(page.getContent()).getElementsByTag("title").text();
            putLemmsIfExistIncreaseValue(page, lemmaScanner.scan(textBody), lemmaScanner.scan(textTitle), siteId);
        }
        count.set(0);
    }

    public void putLemmsIfExistIncreaseValue(Page page, Map<String, Integer> mapBody, Map<String, Integer> mapTitle, int siteId) {

        float rank;
        List<String> tempLemms = new ArrayList<>();

        for (String lemmaBody : mapBody.keySet()) {
            if (mapTitle.containsKey(lemmaBody)) {
                rank = mapBody.get(lemmaBody) * lemmaScanner.getBodyWeight() + mapTitle.get(lemmaBody) * lemmaScanner.getTitleWeight();
                putLemmaInCollection(lemmaBody, mapBody.get(lemmaBody) + mapTitle.get(lemmaBody), siteId, page.getId(), rank);
                tempLemms.add(lemmaBody);
            } else {
                rank = mapBody.get(lemmaBody) * lemmaScanner.getBodyWeight();
                putLemmaInCollection(lemmaBody, mapBody.get(lemmaBody), siteId, page.getId(), rank);
            }
        }

        tempLemms.forEach(mapTitle::remove);

        for (String lemmaTitle : mapTitle.keySet()) {
            rank = mapTitle.get(lemmaTitle) * lemmaScanner.getTitleWeight();
            putLemmaInCollection(lemmaTitle, mapTitle.get(lemmaTitle), siteId, page.getId(), rank);
        }
    }

    public void putLemmaInCollection(String lemmaString, int frequency, int siteId, int pageId, float rank) {
        Lemma lemma = new Lemma(increaseLemmaIdAndGet(), lemmaString, frequency, siteId);
        if (!getLemms().containsKey(lemmaString)) {
            lemms.put(lemmaString, new ArrayList<>(List.of(lemma)));
        } else {
            List<Lemma> lemmaList = getLemms().get(lemmaString);
            List<Integer> siteIdList = lemmaList.stream().map(Lemma::getSiteId).toList();
            if (!siteIdList.contains(siteId)) {
                lemmaList.add(lemma);
                lemms.put(lemmaString, lemmaList);
            } else {
                for (Lemma l : lemmaList) {
                    if (l.getSiteId() != siteId) continue;
                    l.setFrequency(l.getFrequency() + frequency);
                }
            }
        }
        addIndex(new Index(increaseIndexIdAndGet(), pageId, lemma.getId(), rank));
    }

    private String formatPath(String path) {

        int length = path.length();
        if (length > 50) {
            path = path.substring(length - 47);
            path = "..." + path;
        }
        if (length < 50) {
            int dif = 50 - length;
            for (int i = 0; i < dif; i++) {
                path = path.concat(" ");
            }
        }
        return path;
    }

    public void addPages(Set<Page> pages) throws IOException, SQLException {
        for (Page page : pages) {
            addPage(page);
        }
    }

    public void addUsedLinks(Set<String> usedLinksList) {
        for (String s : usedLinksList) {
            addUsedLink(s);
        }
    }
    public boolean getIsStarted() {
        return isStarted;
    }

    public void setStarted(boolean isStarted) {
        this.isStarted = isStarted;
    }

//    public void addThread(Engine task) {
//        taskList.add(task);
//    }

    public void addIndex(Index index) {
        indexes.add(index);
    }

    public void addUsedLink(String usedLink) {
        usedLinks.add(usedLink);
    }

    public void clearPages() {
        pages.clear();
    }

    public void clearLemmas() {
        lemms.clear();
    }

    public void clearIndexes() {
        indexes.clear();
    }

    public void clearUsedLinks() {
        usedLinks.clear();
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public int increaseLemmaIdAndGet() {
        return lemmaId.incrementAndGet();
    }

    public int increasePageIdAndGet() {
        return pageId.incrementAndGet();
    }

    public int increaseIndexIdAndGet() {
        return indexId.incrementAndGet();
    }

    public Set<Page> getPages() {
        return pages;
    }

    public Map<String, List<Lemma>> getLemms() {
        return lemms;
    }

    public Set<String> getUsedLinks() {
        return usedLinks;
    }
}
