package com.search.model;

public class Index {
    int id;
    int pageId;
    int lemmaId;
    float rank;

    public Index(int id, int pageId, int lemmaId, float rank) {
        this.id = id;
        this.pageId = pageId;
        this.lemmaId = lemmaId;
        this.rank = rank;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPageId() {
        return pageId;
    }

    public int getLemmaId() {
        return lemmaId;
    }

    public float getRank() {
        return rank;
    }
}
