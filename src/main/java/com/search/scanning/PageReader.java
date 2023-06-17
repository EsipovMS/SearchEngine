package com.search.scanning;

import com.search.model.Page;
import com.search.model.Site;
import com.search.service.Storage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Indexed;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Indexed
public class PageReader {

    private final String url;
    private final Site site;
    private int code;
    private String content;
    private final Storage storage;
    private final Logger logger = LogManager.getRootLogger();

    public PageReader(String url, Site site, Storage storage) {
        this.url = url;
        this.site = site;
        this.storage = storage;
    }

    public Document getConnection(String url) throws IOException, SQLException {
        Connection connection = Jsoup.connect(url).userAgent("Helicon Search Engine 1.0.0").ignoreHttpErrors(true).maxBodySize(0);
        Connection.Response response;
        Document doc = null;
        connection.timeout(5000);
        int code;
        try {
            response = connection.execute();
            code = response.statusCode();
            setCode(code);
            doc = connection.get();
        } catch (Exception ex) {
            code = 404;
            logger.trace(ex.getMessage());
        }


        if (code >= 400) {
            setContent("NULL. ERROR " + code);
            storage.addPage(new Page(storage.increasePageIdAndGet(), url, code, content, site.getId()));
        } else {
            setContent(doc.outerHtml());
        }
        return doc;
    }

    public List<PageReader> getChildrens() throws IOException, SQLException {
        Document doc = getConnection(url);
        if (doc == null) {
            return new ArrayList<>();
        }
        List<String> children = doc.getElementsByTag("a")
                .stream()
                .map(l -> {
                    String tag = String.valueOf(l);
                    int start = tag.indexOf("href=\"") + 6;
                    int end = tag.indexOf("\"", start);
                    try {
                        return tag.substring(start, end);
                    } catch (StringIndexOutOfBoundsException ex) {
                        logger.trace(ex.getMessage());
                        return "";
                    }
                })
                .toList();
        return filterChildren(children)
                .stream()
                .map(c -> new PageReader(c, site, storage))
                .toList();
    }

    public List<String> filterChildren(List<String> children) {

        List<String> links = children.stream()
                .filter(l -> !storage.getUsedLinks().contains(l))
                .map(l -> {
                    if (l.startsWith("/")) {
                        storage.addUsedLink(l);
                        l = url + l;
                    }
                    l = l.replaceAll("//", "/");
                    l = l.replaceAll("https:/", "https://");
                    l = l.replaceAll("http:/", "http://");

                    return l;
                })
                .filter(lf -> !storage.getUsedLinks().contains(lf))
                .filter(l -> !l.equals(url))
                .filter(l -> l.contains(url))
                .filter(l -> l.startsWith("https:/") || l.startsWith("http:/"))
                .filter(l -> !l.contains(".pdf"))
                .filter(l -> !l.contains(".svg"))
                .filter(l -> {
                    if (l.length() < url.length()) {
                        return false;
                    }
                    return l.substring(0, url.length()).contains(url);
                })
                .filter(l -> l.length() <= 190)
                .toList();
        return links;
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

    public String getUrl() {
        return url;
    }

}



