package com.search.service;

import com.search.model.Page;
import com.search.model.Site;
import com.search.model.enums.Status;
import com.search.scanning.Engine;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;

@Service
public class EngineService {
    private ForkJoinPool forkJoinPool = new ForkJoinPool(8);
    private List<Site> sites = new ArrayList<>();
    private boolean success = true;

    private final List<Engine> engines = new ArrayList<>();
    private final LemmaScanner lemmaScanner;
    private final Storage storage;
    private final DBConnector dbConnector;
    private final Logger logger;
    private final DBConnector fastDBConnector;


    public EngineService(LemmaScanner lemmaScanner, Storage storage, DBConnector dbConnector, Logger logger, DBConnector fastDBConnector) {
        this.lemmaScanner = lemmaScanner;
        this.storage = storage;
        this.dbConnector = dbConnector;
        this.logger = logger;
        this.fastDBConnector = fastDBConnector;
    }

    public void startIndexing() {
        if (storage.getIsStarted()) {
            return;
        }
        resetScanningParams();
        sites = dbConnector.getSites();
        List<Starter> starters = new ArrayList<>();
        for (Site site : sites) {
            Engine engine = new Engine(site, storage);
            engines.add(engine);
            site.setStatus(Status.INDEXING);
            site.setStatusDateTime(LocalDateTime.now());
            dbConnector.updateSite(site);

            Starter starter = new Starter(engine);
            starters.add(starter);
        }

        Listener listener = new Listener(starters);

        starters.forEach(Thread::start);
        listener.start();
        storage.setStarted(false);
    }


    public boolean stopIndexing() {
        if (engines.isEmpty()) {
            return false;
        }
        storage.setStop(true);
        success = false;
        dbConnector.setStop(true);
        forkJoinPool.shutdownNow();
        logger.info("Сканирование остановлено, идет сохранение данных");
        List<Site> sites = dbConnector.getSites();
        for (Site site : sites) {
            site.setStatus(Status.FAILED);
            site.setLastError("Остановлено пользователем");
            site.setStatusDateTime(LocalDateTime.now());
            dbConnector.updateSite(site);
        }
        try {
            storage.saveAllData();
        } catch (IOException | SQLException e) {
            logger.debug(e.getMessage());
        }
        storage.setStarted(false);
        logger.info("Сканирование остановлено, данные сохранены");
        return true;
    }


    public boolean startIndexingPage(String urlPage) throws IOException, SQLException {
        List<String> urlSites = dbConnector.getSites().stream().map(Site::getUrl).toList();
        boolean itContains = false;
        for (String urlSite : urlSites) {
            if (!urlPage.contains(urlSite)) continue;
            itContains = true;
        }
        if (!itContains) return false;
        Connection connection = Jsoup.connect(urlPage).userAgent("Helicon Search Engine 1.0.0").ignoreHttpErrors(true).maxBodySize(0);
        Connection.Response response;
        Document doc;
        int code;
        Optional<Site> optionalSite = dbConnector.getSites().stream().filter(s -> urlPage.contains(s.getUrl())).findAny();
        Site site = optionalSite.orElse(null);
        if (site == null) return false;
        try {
            response = connection.execute();
            code = response.statusCode();
            doc = connection.get();
        } catch (ConnectException | RuntimeException ex) {
            doc = null;
            code = 404;
            logger.trace(ex.getMessage());
        }
        String content;
        if (code >= 400) {
            content = "NULL. ERROR " + code;
        } else {
            content = doc.outerHtml();
        }
        storage.addPage(new Page(storage.increasePageIdAndGet(), urlPage, code, content, site.getId()));
        PageSaver pageSaver = new PageSaver();
        pageSaver.start();
        return true;
    }

    public JSONObject getStatistics() throws SQLException {

        int pages = 0;
        int lemmas = 0;
        boolean isIndexing;

        List<Site> sites = dbConnector.getSites();
        JSONObject result = new JSONObject();
        JSONObject statistics = new JSONObject();
        JSONObject total = new JSONObject();
        List<Status> statuses = new ArrayList<>();

        for (Site site : sites) {
            int sitePages = fastDBConnector.getCountPagesBySite(site);
            int siteLemmas = fastDBConnector.getCountLemmasBySite(site);

            JSONObject detailed = new JSONObject();
            detailed.put("url", site.getUrl());
            detailed.put("name", site.getName());
            detailed.put("status", site.getStatus());
            detailed.put("statusTime", site.getStatusDateTime());
            detailed.put("error", site.getLastError());
            detailed.put("pages", sitePages);
            detailed.put("lemmas", siteLemmas);
            pages += sitePages;
            lemmas += siteLemmas;
            Status siteStatus = site.getStatus();
            statuses.add(siteStatus);
            statistics.append("detailed", detailed);
        }
        isIndexing = statuses.get(0).equals(Status.INDEXING) || statuses.get(1).equals(Status.INDEXING) || statuses.get(2).equals(Status.INDEXING);
        total.put("sites", sites.size());
        total.put("pages", pages);
        total.put("lemmas", lemmas);
        total.put("isIndexing", isIndexing);

        statistics.put("total", total);
        result.put("result", true);
        result.put("statistics", statistics);
        return result;
    }

    private void resetScanningParams() {
        success = true;
        engines.clear();
        storage.clearUsedLinks();
        storage.setStop(false);
        this.forkJoinPool = new ForkJoinPool(8);
        storage.setStarted(true);
        dbConnector.deleteSiteIndexes();
        dbConnector.setStop(false);
        lemmaScanner.getWeights();
    }

    class Starter extends Thread {

        Engine engine;

        Starter(Engine engine) {
            this.engine = engine;
        }

        @Override
        public void run() {
            logger.info("Индексация сайта " + engine.getRootUrl() + " началась");
            try {
                forkJoinPool.invoke(engine);
            } catch (CancellationException ex) {
                logger.debug(ex.getMessage());
            }
            if (success) {
                logger.info("Индексация сайта " + engine.getRootUrl() + " завершилась успешно");
            }

        }
    }

    class Listener extends Thread {

        List<Starter> starters;

        Listener(List<Starter> starters) {
            this.starters = starters;
        }

        @Override
        public void run() {
            starters.forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            });
            try {
                storage.saveAllData();
                if (success) {
                    for (Site site : sites) {
                        site.setStatus(Status.INDEXED);
                        site.setStatusDateTime(LocalDateTime.now());
                        site.setLastError("");
                        dbConnector.updateSite(site);
                    }
                    logger.info("Индексация полностью завершена");
                }
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }
    }
    class PageSaver extends Thread {
        @Override
        public void run() {
            try {
                storage.createLemmsAndIndexesAndCollect();
                storage.saveAllData();
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
