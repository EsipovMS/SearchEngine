package com.search.service;

import com.search.model.*;
import com.search.model.enums.Status;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.springframework.stereotype.Service;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
@Service
public class DBConnector {

    private SessionFactory sessionFactory = null;
    private Session session = null;
    boolean stop = false;
    private Connection connection;
    private final Logger logger;
    private final String dbUser = "root";
    private final String url = "jdbc:mysql://localhost:3306/search_engine?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
    private final String dbPass = "testtest55";


    public DBConnector(Logger logger) {
        this.logger = logger;
    }

    public Connection getConnection() {
        if (connection == null) {
            try {

                connection = DriverManager.getConnection(url + "&user=" + dbUser + "&password=" + dbPass);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return connection;
    }

    public synchronized void saveAllIndexes(List<Index> indexList) throws SQLException {
        String insert = "INSERT INTO indexes (_rank, lemma_id, page_id) VALUES ";
        int progress = 0;
        StringBuilder sql = new StringBuilder();
        sql.append(insert);
        if (indexList.size() == 0) return;
        for (Index index : indexList) {
            String values = "(" + index.getRank() + ", " + index.getLemmaId() + ", " + index.getPageId() + "), ";
            sql.append(values);
            progress++;
            if (progress % 10_000 == 0) {
                logger.info(String.format("Подготовка запроса вставки индексов %s из %s", progress, indexList.size()));
            }
        }
        sql.replace(sql.lastIndexOf(", "), sql.length(), ";");

        try {
            getConnection().createStatement().execute(sql.toString());
        } catch (SQLSyntaxErrorException ex) {
            logger.warn("Error in sql: " + sql);
        }
        logger.info(indexList.size() + " индексов успешно записаны");
    }

    public synchronized void saveAllLemms(Map<String, List<Lemma>> lemmaListMap) throws SQLException {
        String insert = "INSERT INTO lemma (id, frequency, lemma, site_id) VALUES ";
        int progress = 0;
        StringBuilder sql = new StringBuilder();
        sql.append(insert);
        if (lemmaListMap.size() == 0) return;

        for (List<Lemma> lemmaList : lemmaListMap.values()) {
            for (Lemma lemma : lemmaList) {
                if (lemma.getLemma().equals("")) continue;
                String values = "(" + lemma.getId() + ", "+ lemma.getFrequency() + ", " + "'" + lemma.getLemma() + "'" + ", " + lemma.getSiteId() + "), ";
                sql.append(values);
                progress++;
                if (progress % 1_000 == 0) {
                    logger.info(String.format("Подготовка запроса вставки лемм %s из %s", progress, lemmaListMap.size()));
                }
            }
        }
        sql.replace(sql.lastIndexOf(", "), sql.length(), ";");

        try {
            getConnection().createStatement().execute(sql.toString());
        } catch (SQLSyntaxErrorException ex) {
            logger.warn("Error in sql: " + sql);
        }
        logger.info(lemmaListMap.size() + " лемм успешно записаны");
    }

    public synchronized void saveAllPages(Set<Page> pages) throws SQLException {
        String insert = "INSERT INTO page (id, code, content, path, site_id) VALUES (?, ?, ?, ?, ?)";
        int progress = 0;
        try (
                PreparedStatement statement = connection.prepareStatement(insert)
        ) {
            int i = 0;

            for (Page page : pages) {
                statement.setInt(1, page.getId());
                statement.setInt(2, page.getCode());
                statement.setString(3, "'" + page.getContent() + "'");
                statement.setString(4, "'" + page.getPath() + "'");
                statement.setInt(5, page.getSiteId());

                statement.addBatch();
                i++;
                progress++;
                if (progress % 50 == 0) {
                    logger.info(String.format("Подготовка запроса вставки страниц %s из %s", progress, pages.size()));
                }
                if (i % 1000 == 0 || i == pages.size()) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
            logger.info(pages.size() + " страниц успешно сохранено");
        }
    }

    public synchronized int getCountPagesBySite(Site site) throws SQLException {
        String select = "SELECT COUNT(*) FROM page WHERE site_id = " + site.getId();
        ResultSet rs = getConnection().createStatement().executeQuery(select);
        if (!rs.next()) {
            return 0;
        }
        return rs.getInt(1);
    }

    public synchronized int getCountLemmasBySite(Site site) throws SQLException {
        String select = "SELECT COUNT(*) FROM lemma WHERE site_id = " + site.getId();
        ResultSet rs = getConnection().createStatement().executeQuery(select);
        if (!rs.next()) {
            return 0;
        }
        return rs.getInt(1);
    }

    public Site getSiteById(int siteId) throws SQLException {
        String select = "SELECT * FROM site WHERE id = " + siteId;
        ResultSet rs = getConnection().createStatement().executeQuery(select);
        if (!rs.next()) {
            return null;
        }
        Status status = Status.valueOf(rs.getString("status"));
        String lastError = rs.getString("last_error");
        String url = rs.getString("url");
        String name = rs.getString("name");
        return new Site(status, lastError, url, name);
    }

    public List<Page> getPagesByLemma(String lemmaString) throws SQLException {
        List<Page> pagesByLemma = new ArrayList<>();
        List<Lemma> lemmaList = getLemmaList(lemmaString);
        if(lemmaList == null) return new ArrayList<>();
        if (lemmaList.isEmpty()) return new ArrayList<>();

        StringBuilder lemmaIdSet = new StringBuilder();
        for (Lemma lemma : lemmaList) {
            lemmaIdSet.append("lemma_id = ");
            lemmaIdSet.append(lemma.getId());
            if (!lemma.equals(lemmaList.get(lemmaList.size() - 1))) lemmaIdSet.append(" OR ");
        }
        String select = "SELECT page_id FROM `indexes` WHERE " + lemmaIdSet.toString();
        ResultSet rs = getConnection().createStatement().executeQuery(select);

        if (!rs.next()) {
            return new ArrayList<>();
        }
        do {
            int id = rs.getInt("page_id");
            pagesByLemma.add(getPageById(id));
        } while (rs.next());
        return pagesByLemma;
    }

    private Page getPageById(int id) throws SQLException {
        String select = "SELECT * FROM page WHERE id = " + id;
        ResultSet rs = getConnection().createStatement().executeQuery(select);

        if (!rs.next()) {
            return null;
        }
        String path = rs.getString("path");
        int code = rs.getInt("code");
        String content = rs.getString("content");
        int siteId = rs.getInt("site_id");
        return new Page(id, path, code, content, siteId);
    }

    private List<Lemma> getLemmaList(String lemmaString) throws SQLException {
        String select = "SELECT * FROM lemma WHERE lemma = '" + lemmaString + "'";
        ResultSet rs = getConnection().createStatement().executeQuery(select);
        if (!rs.next()) {
            return null;
        }
        List<Lemma> lemmaList = new ArrayList<>();
        do {
            int id = rs.getInt("id");
            int frequency = rs.getInt("frequency");
            int siteId = rs.getInt("site_id");
            Lemma lemma = new Lemma(id, lemmaString, frequency, siteId);
            lemmaList.add(lemma);
        }while (rs.next());
        return lemmaList;
    }

    public synchronized void getSession() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().configure("hibernate.cfg.xml").build();
        Metadata metadata = new MetadataSources(registry).getMetadataBuilder().build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
        session = sessionFactory.openSession();
    }

    public synchronized void closeSession() {
        session.close();
        sessionFactory.close();
    }

    public synchronized void updateSite(Site site) {
        getSession();
        Transaction tx = session.beginTransaction();
        session.saveOrUpdate(site);
        tx.commit();
        closeSession();
    }


    public synchronized float getWeightFieldBySelector(String selector) {
        getSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<Field> query = builder.createQuery(Field.class);
        Root<Field> root = query.from(Field.class);
        query.select(root).where(builder.equal(root.get("selector"), selector));
        List<Field> fieldList = session.createQuery(query).getResultList();
        closeSession();
        return fieldList.get(0).getWeight();
    }

    public synchronized List<Site> getSites() {
        getSession();
        CriteriaBuilder siteBuilder = session.getCriteriaBuilder();
        CriteriaQuery<Site> siteQuery = siteBuilder.createQuery(Site.class);
        Root<Site> siteRoot = siteQuery.from(Site.class);
        siteQuery.select(siteRoot);
        List<Site> sites = session.createQuery(siteQuery).getResultList();
        closeSession();
        return sites;
    }

    public synchronized void deleteSiteIndexes() {
        getSession();
        Transaction tx = session.getTransaction();
        tx.begin();
        session.createSQLQuery("TRUNCATE TABLE page").executeUpdate();
        session.createSQLQuery("TRUNCATE TABLE lemma").executeUpdate();
        session.createSQLQuery("TRUNCATE TABLE indexes").executeUpdate();
        tx.commit();
        closeSession();
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }
}
