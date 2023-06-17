package com.search.model;

public class SearchResult {

    Page page;
    String snippet;
    float absoluteRel;
    float relativeRel = 0;

    public SearchResult(Page page, String snippet, float absoluteRel) {
        this.page = page;
        this.absoluteRel = absoluteRel;
        this.snippet = snippet;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public float getAbsoluteRel() {
        return absoluteRel;
    }

    public float getRelativeRel() {
        return relativeRel;
    }

    public void setRelativeRel(float relativeRel) {
        this.relativeRel = relativeRel;
    }

    public String getSnippet() {
        return snippet;
    }
}
