package com.search.model;

public class Lemma implements Comparable<Lemma> {
    private int id;
    private String lemma;
    private int frequency;

    private int siteId;

    public Lemma(int id, String lemma, int frequency) {
        this.id = id;
        this.lemma = lemma;
        this.frequency = frequency;
    }

    public Lemma(int id, String lemma, int frequency, int siteId) {
        this.id = id;
        this.lemma = lemma;
        this.frequency = frequency;
        this.siteId = siteId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLemma() {
        return lemma;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getSiteId() {
        return siteId;
    }

    @Override
    public int compareTo(Lemma o) {
        return Integer.compare(o.getFrequency(), this.getFrequency());
    }
}
