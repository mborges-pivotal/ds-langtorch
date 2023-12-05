package com.datastax.da.vsearch.langtorch;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.dtsx.astra.sdk.AstraDB;

import io.stargate.sdk.json.domain.CollectionDefinition;
import io.stargate.sdk.json.domain.JsonResult;

@Component
public class AstraDbVectorStore {

    private static Logger logger = LoggerFactory.getLogger(AstraDbVectorStore.class);

    private String tableName = "vs_4seasons_openai";
    private String embeddingColumn = "vector";
    private String selectString = "SELECT body_blob, similarity_cosine(%s, %s) FROM %s ORDER BY %s ANN OF %s LIMIT 3";

    // @Autowired
    // private AstraClient client;

    @Value("${ASTRA_DB_APPLICATION_TOKEN}")
    private String astraToken;

    @Value("${ASTRA_DB_API_ENDPOINT}")
    private String astraApiEndpoint;

    @Autowired
    private CqlSession session;

    private AstraDB db;
    private CollectionDefinition col;

    // No Args Constructor
    public AstraDbVectorStore() {
        db = new AstraDB(astraToken, astraApiEndpoint);
        // doc says "collection" instead of "findCollection"
        col = db.findCollection("FourSeasonsSite").get();
    }

    // message is a string representation of a vector
    public CompletionStage<AsyncResultSet> searchWebsite(String message)
            throws InterruptedException, ExecutionException {

        // List<JsonResult> resultsSet = col.similaritySearch(new float[] { 0.15f, 0.1f, 0.1f, 0.35f, 0.55f }, 10);
        // resultsSet.stream().forEach(System.out::println);

        String stmtString = String.format(selectString, embeddingColumn, message, tableName, embeddingColumn, message);
        // logger.debug("statement: {}", stmtString);
        // logger.debug("message: {}", message);

        // TODO: Look into using prepared statement
        // see https://docs.datastax.com/en/developer/java-driver/4.17/upgrade_guide/
        // for Vector info
        // PreparedStatement ps = session.prepare(selectString);
        // CompletionStage<AsyncResultSet> rs = session.executeAsync(ps.bind(message));


        return session.executeAsync(stmtString);
    }

    //////////////////////////////////////////
    // Helper Methods
    //////////////////////////////////////////

}
