package com.search.model;

public class Page {
    private int id;
    private String path;
    private int code;
    private String content;
    private int siteId;

    public Page(int id, String path, int code, String content, int siteId) {
        this.id = id;
        this.path = path;
        this.code = code;
        if(content != null) {
            this.content = content;
        } else {
            this.content = "NULL";
        }
        this.siteId = siteId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSiteId() {
        return siteId;
    }
}
