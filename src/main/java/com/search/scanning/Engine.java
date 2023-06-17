package com.search.scanning;

import com.search.model.Site;
import com.search.service.Storage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

public class Engine extends RecursiveAction {

    private String rootUrl;
    private List<PageReader> childrens = new ArrayList<>();
    private PageReader pageReader;
    private Site site;
    private final Storage storage;
    private final Logger logger = LogManager.getRootLogger();

    public Engine(Storage storage) {
        this.storage = storage;
    }

    public Engine(String rootUrl, Site site, Storage storage) {
        this.rootUrl = rootUrl;
        this.site = site;
        this.storage = storage;
        this.pageReader = new PageReader(rootUrl, site, storage);
    }

    public Engine(Site site, Storage storage) {
        this.rootUrl = site.getUrl();
        this.site = site;
        this.pageReader = new PageReader(rootUrl, site, storage);
        this.storage = storage;
    }

    @Override
    protected void compute() {
        if (storage.isStop()) return;
        try {
            childrens = pageReader.getChildrens();
        } catch (IOException | SQLException ex) {
            logger.trace(ex.getMessage());
        }
        if (childrens.size() == 0) {
            return;
        }
        storage.addUsedLinks(childrens.stream().map(PageReader::getUrl).collect(Collectors.toSet()));

        try {
            createPagesAndCollect();
        } catch (InterruptedException | IOException | SQLException e) {
            logger.trace(e.getMessage());
        }

        for (PageReader children : childrens) {
            Engine task = new Engine(children.getUrl(), site, storage);
            task.fork();
            task.join();
        }
    }

    public void createPagesAndCollect() throws InterruptedException, IOException, SQLException {
        if (storage.isStop()) return;
        if (childrens.size() / 8 > 1) {
            List<PageReader> part1 = childrens.subList(0, (childrens.size()) / 4);
            List<PageReader> part2 = childrens.subList(part1.size() + 1, (childrens.size()) / 2);
            List<PageReader> part3 = childrens.subList(part1.size() + part2.size() + 1, (childrens.size()) / 4 * 3);
            List<PageReader> part4 = childrens.subList(part1.size() + part2.size() + part3.size() + 1, childrens.size());

            ChildrensRunner childrensRunner1 = new ChildrensRunner(part1, site, storage);
            ChildrensRunner childrensRunner2 = new ChildrensRunner(part2, site, storage);
            ChildrensRunner childrensRunner3 = new ChildrensRunner(part3, site, storage);
            ChildrensRunner childrensRunner4 = new ChildrensRunner(part4, site, storage);

            List<ChildrensRunner> childrensRunnerList = new ArrayList<>();

            childrensRunnerList.add(childrensRunner1);
            childrensRunnerList.add(childrensRunner2);
            childrensRunnerList.add(childrensRunner3);
            childrensRunnerList.add(childrensRunner4);


            for (ChildrensRunner childrensRunner : childrensRunnerList) {
                childrensRunner.start();
            }

            for (ChildrensRunner childrensRunner : childrensRunnerList) {
                childrensRunner.join();
            }

        } else {
            ChildrensRunner childrensRunner = new ChildrensRunner(childrens, site, storage);
            childrensRunner.run();
            storage.addPages(childrensRunner.getPageList());
            childrensRunner.clear();
        }
    }

    public String getRootUrl() {
        return rootUrl;
    }

}
