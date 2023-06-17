package com.search.service;

import com.search.model.Lemma;
import com.search.model.Page;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class LemmaScanner {

    private final LuceneMorphology luceneMorph = new RussianLuceneMorphology();
    private final DBConnector dbConnector;
    private float titleWeight;
    private float bodyWeight;

    public LemmaScanner(DBConnector dbConnector) throws IOException {

        this.dbConnector = dbConnector;
    }

    public Map<String, Integer> scan(String text) {
        Map<String, Integer> lemmsMap = new HashMap<>();
        List<String> words = splitText(text);
        List<String> official = new ArrayList<>();
        for (String word : words) {
            try {
                List<String> infos = luceneMorph.getMorphInfo(word);
                StringBuilder infoString = new StringBuilder();
                for (String info : infos) {
                    infoString.append(info);
                }
                if (infoString.toString().contains("СОЮЗ")
                        || infoString.toString().contains("МЕЖД")
                        || infoString.toString().contains("ПРЕДЛ")
                        || infoString.toString().contains("ЧАСТ")) {
                    official.add(word);
                }
            } catch (WrongCharaterException | ArrayIndexOutOfBoundsException ex) {
                official.add(word);
            }
        }
        words.removeAll(official);
        for (String word : words) {
            List<String> wordBaseForms = luceneMorph.getNormalForms(word);
            for (String form : wordBaseForms) {
                if (lemmsMap.containsKey(form)) {
                    int value = lemmsMap.get(form) + 1;
                    lemmsMap.replace(form, value);
                } else {
                    lemmsMap.put(form, 1);
                }
            }
        }
        return lemmsMap;
    }

    public Map<String, Float> getLemmsForPageBySearchWords(Page page, List<Lemma> lemmaList) {

        Map<String, Integer> bodyLemms = scan(Jsoup.parse(page.getContent()).text());

        Map<String, Float> lemmsForPageBySearchWords = new HashMap<>();
        for (Lemma lemma : lemmaList) {
            if (bodyLemms.containsKey(lemma.getLemma())) {
                lemmsForPageBySearchWords.put(lemma.getLemma(), bodyLemms.get(lemma.getLemma()) * getBodyWeight());
            }
        }
        Map<String, Integer> titleLemms = scan(Jsoup.parse(page.getContent()).title());

        for (Lemma lemma : lemmaList) {
            if (titleLemms.containsKey(lemma.getLemma())) {
                lemmsForPageBySearchWords.put(lemma.getLemma(), titleLemms.get(lemma.getLemma()) * getTitleWeight());
            }
        }
        return lemmsForPageBySearchWords;
    }

    public List<String> splitText(String text) {
        text = text.replaceAll("[\\p{P}\\p{S}]", "").toLowerCase();
        text = text.replaceAll(" —", "");
        text = text.replaceAll("\\s+", " ");

        return (ArrayList<String>) new ArrayList(Arrays.asList(Arrays.stream(text.split(" ")).toArray()));
    }

    public List<String> getNormalForm(String word) {
        List<String> normalforms = new ArrayList<>();
        try {
            normalforms.addAll(luceneMorph.getNormalForms(word));
            return normalforms;
        } catch (WrongCharaterException | ArrayIndexOutOfBoundsException ex) {
            normalforms.add(word);
            return normalforms;
        }
    }

    public float getBodyWeight() {
        return bodyWeight;
    }

    public float getTitleWeight() {
        return titleWeight;
    }

    public void getWeights() {
        titleWeight = dbConnector.getWeightFieldBySelector("body");
        bodyWeight = dbConnector.getWeightFieldBySelector("title");
    }
}
