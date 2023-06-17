package com.search.scanning;

import com.search.service.Storage;
import com.search.model.Page;
import com.search.model.Site;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ChildrensRunner extends Thread{

    private final List<PageReader> childrens;
    private final Set<Page> pageList = new HashSet<>();
    private final Site site;
    private final Storage storage;
    private final Logger logger = LogManager.getLogger(ChildrensRunner.class);

    public ChildrensRunner(List<PageReader> childrens, Site site, Storage storage) {
        this.childrens = childrens;
        this.site = site;
        this.storage = storage;
    }

    @Override
    public void run() {
        for (PageReader children : childrens) {
            if(storage.isStop()) {
                break;
            }
            String path = children.getUrl();
            int code;
            String content;

            try {
                children.getConnection(path);
                code = children.getCode();
                content = children.getContent();
                Page page = new Page(storage.increasePageIdAndGet(), path, code, content, site.getId());
                logger.debug("Страница: " + page.getPath() + " сайт " + site.getUrl());
                storage.addPage(page);
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Set<Page> getPageList() {
        return pageList;
    }

    public void clear() {
        pageList.clear();
    }
}
