package com.search.controller;

import com.search.model.SearchResult;
import com.search.service.DBConnector;
import com.search.service.Storage;
import com.search.service.LemmaScanner;
import com.search.service.EngineService;
import com.search.service.SearchService;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Controller
public class DefaultController {

    private final EngineService engineService;
    private final SearchService searchService;
    private final Storage storage;

    private final DBConnector dbConnector;

    private final LemmaScanner lemmaScanner;

    public DefaultController(EngineService engineService,
                             SearchService searchService,
                             Storage storage,
                             DBConnector dbConnector,
                             LemmaScanner lemmaScanner) {
        this.engineService = engineService;
        this.searchService = searchService;
        this.storage = storage;
        this.dbConnector = dbConnector;
        this.lemmaScanner = lemmaScanner;
    }

    @GetMapping("/")
    public String admin() {
        return index();
    }

    @GetMapping("/admin")
    public String index() {
        lemmaScanner.getWeights();
        return "index";
    }

    @GetMapping("/startIndexing")
    @ResponseBody
    public String startIndexing() throws InterruptedException {
        boolean started = storage.getIsStarted();
        JSONObject result = new JSONObject();
        if(!started) {
            engineService.startIndexing();
            result.put("result", true);
        } else {
            result.put("result" , false);
            result.put("error", "Индексация уже запущена");
        }
        return result.toString();
    }

    @GetMapping("/stopIndexing")
    @ResponseBody
    public String stopIndexing() throws InterruptedException, IOException {
        boolean stop = engineService.stopIndexing();
        JSONObject result = new JSONObject();
        if (stop) {
            result.put("result", true);
        } else {
            result.put("result", false);
            result.put("error", "Индексация не запущена");
        }
        return result.toString();
    }

    @PostMapping("/indexPage")
    @ResponseBody
    public String indexPage(@RequestParam String url) throws InterruptedException, IOException, SQLException {
        JSONObject result = new JSONObject();

        boolean start = engineService.startIndexingPage(url);
        if(start) {
            result.put("result", true);
        } else {
            result.put("result", false);
            result.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
        return result.toString();
    }

    @GetMapping("/statistics")
    @ResponseBody
    public String statistics() throws SQLException {
        JSONObject statistics = engineService.getStatistics();
        return statistics.toString();
    }

    @GetMapping("/search")
    @ResponseBody
    public String search(@RequestParam String query,
                         @RequestParam(required = false) int offset,
                         @RequestParam(required = false) int limit,
                         @RequestParam(required = false, name = "site") String siteUrl) throws IOException, SQLException {

        List<SearchResult> searchResults = searchService.search(query);

        if (siteUrl != null) {
            searchResults = searchService.filterBySite(searchResults, siteUrl);
        }
        if (offset != 0) {
            searchResults = searchResults.subList(offset - 1, searchResults.size());
        }
        if(limit != 0 && searchResults.size() >= limit) {
            searchResults = searchResults.subList(0, limit);
        }

        return searchService.toJSONObject(searchResults).toString();
    }



}
