package com.search.model;

import com.search.model.enums.Status;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "status_time")
    private LocalDateTime statusDateTime;
    @Column(name = "last_error")
    private String lastError;

    private String url;

    private String name;

    public Site() {
    }
    public Site(Status status, String lastError, String url, String name) {
        this.status = status;
        this.lastError = lastError;
        this.url = url;
        this.name = name;
    }

    public Site(Status status, LocalDateTime statusDateTime, String lastError, String url, String name) {
        this.status = status;
        this.statusDateTime = statusDateTime;
        this.lastError = lastError;
        this.url = url;
        this.name = name;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getStatusDateTime() {
        return statusDateTime;
    }

    public void setStatusDateTime(LocalDateTime statusDateTime) {
        this.statusDateTime = statusDateTime;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }
}


