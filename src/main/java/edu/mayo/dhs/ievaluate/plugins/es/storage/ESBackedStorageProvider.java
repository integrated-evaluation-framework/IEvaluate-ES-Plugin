package edu.mayo.dhs.ievaluate.plugins.es.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.applications.ApplicationProvider;
import edu.mayo.dhs.ievaluate.api.applications.ProfiledApplication;
import edu.mayo.dhs.ievaluate.api.storage.StorageProvider;
import edu.mayo.dhs.ievaluate.plugins.es.config.ESPluginConfig;
import edu.mayo.dhs.ievaluate.plugins.es.models.ProfiledApplicationBean;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class ESBackedStorageProvider implements StorageProvider {

    private RestHighLevelClient esClient;
    private ESPluginConfig config;


    public ESBackedStorageProvider(ESPluginConfig conf) {
        this.config = conf;
        RestClientBuilder builder = RestClient
                .builder(new HttpHost(conf.getHostName(), conf.getPort(), conf.getHttpSchema()))
                .setRequestConfigCallback(requestConfigBuilder -> {
                    return requestConfigBuilder.setConnectTimeout(30000).setSocketTimeout(30000); // 30 second timeout
                }).setMaxRetryTimeoutMillis(30000);
        Header[] headers = new Header[]{new BasicHeader("Content-Type", "application/json"), new BasicHeader("Role", "Read")};
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(conf.getUser(), conf.getPass()));
        builder.setDefaultHeaders(headers).setHttpClientConfigCallback(clientBuilder -> clientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        this.esClient = new RestHighLevelClient(builder);
    }

    @Override
    public Map<String, JsonNode> loadRegisteredApplications() {
        Map<String, JsonNode> registeredApplications = new HashMap<>();
        ObjectMapper om = new ObjectMapper();
        boolean success = false;
        String scrollUID = null;
        for (int i = 0; i < 5 && !success; i++) {
            try {
                SearchRequest req = new SearchRequest()
                        .indices(config.getApplicationIndex())
                        .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
                        .scroll(TimeValue.timeValueMinutes(5));
                SearchResponse resp = esClient.search(req, RequestOptions.DEFAULT);
                scrollUID = resp.getScrollId();
                SearchHit[] hits = resp.getHits().getHits();
                while (hits != null && hits.length > 0) {
                    for (SearchHit hit : hits) {
                        try {
                            String raw = hit.getSourceAsString();
                            ProfiledApplicationBean bean = om.readValue(raw, ProfiledApplicationBean.class);
                            registeredApplications.put(bean.getClassType(), om.readTree(bean.getValue()));
                        } catch (Throwable t) {
                            IEvaluate.getLogger().warn("Could not read in application " + new String(Base64.getDecoder().decode(hit.getId())) + ", skipping", t);
                        }
                    }
                    SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollUID);
                    scrollRequest.scroll(scrollUID);
                    resp = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                    scrollUID = resp.getScrollId();
                    hits = resp.getHits().getHits();
                }
                success = true;
            } catch (Throwable e) {
                IEvaluate.getLogger().warn("Error in loading, retrying; iteration: " + i, e);
                if (scrollUID != null) { // Try to free resources
                    try {
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                        clearScrollRequest.addScrollId(scrollUID);
                        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                    } catch (IOException ignored) {
                    }
                    scrollUID = null;
                }
            }
        }
        // Cleanup
        if (scrollUID != null) {
            try {
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(scrollUID);
                esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            } catch (IOException ignored) {}
        }
        if (!success) {
            IEvaluate.getLogger().error("Error in loading, passed retry threshold, data may be lost");
            return Collections.emptyMap();
        }
        return registeredApplications;
    }

    @Override
    public void saveRegisteredApplications() {
        LinkedBlockingDeque<ProfiledApplicationBean> registeredApplications = new LinkedBlockingDeque<>();
        ObjectMapper om = new ObjectMapper();
        for (ProfiledApplication app : IEvaluate.getApplicationManager().getRegisteredApplications()) {
            try {
                ApplicationProvider<?> pertinentProvider = IEvaluate
                        .getApplicationManager()
                        .getApplicationProviders()
                        .get(app.getClass().getName());
                if (pertinentProvider == null) {
                    throw new IllegalArgumentException(
                            "Application is of type "
                                    + app.getClass().getName()
                                    + " but no suitable application provider was found"
                    );
                }
                registeredApplications.add(new ProfiledApplicationBean(app.getClass().getName(), om.writer().writeValueAsString(pertinentProvider.marshal(app)), app.getId().toString()));
            } catch (Throwable t) {
                IEvaluate.getLogger().error("Failure marshalling " + app.getName() + " with ID " + app.getId(), t);
                IEvaluate.getLogger().error("Data may be lost");
            }
        }
        // Now serialize in batches of 1000
        List<ProfiledApplicationBean> work = new ArrayList<>(1000);
        registeredApplications.drainTo(work, 1000);
        while (registeredApplications.size() > 0) {
            BulkRequest req = new BulkRequest();
            for (ProfiledApplicationBean bean : work) {
                req.add(new IndexRequest(config.getApplicationIndex())
                        .id(Base64.getEncoder().encodeToString(bean.getAppId().getBytes()))
                        .type("ProfiledApplication")
                        .source(om.valueToTree(bean), XContentType.JSON));
            }
            boolean success = false;
            for (int i = 0; i < 5 && !success; i++) {
                try {
                    BulkResponse resp = esClient.bulk(req, RequestOptions.DEFAULT);
                    success = !resp.hasFailures();
                    if (!success) {
                        IEvaluate.getLogger().warn("Error in saving, retrying; iteration: " + i + ", error: " + resp.buildFailureMessage());
                    }
                } catch (Throwable t) {
                    IEvaluate.getLogger().warn("Error in saving, retrying; iteration: " + i, t);
                }
            }
            if (!success) {
                IEvaluate.getLogger().error("Error in saving, passed retry threshold, data may be lost");
            }
            work.clear();
            registeredApplications.drainTo(work, 1000);
        }
    }
}
