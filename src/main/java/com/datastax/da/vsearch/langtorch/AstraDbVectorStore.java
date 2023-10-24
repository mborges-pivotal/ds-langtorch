package com.datastax.da.vsearch.langtorch;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;

@Component
public class AstraDbVectorStore {

    private static Logger logger = LoggerFactory.getLogger(AstraDbVectorStore.class);

    private String tableName = "vs_4seasons_openai";
    private String embeddingColumn = "vector";
    private String selectString = "SELECT body_blob FROM %s ORDER BY %s ANN OF %s LIMIT 5";

    @Autowired
    private AstraClient client;

    // message is a string representation of a vector
    public CompletionStage<AsyncResultSet> searchWebsite(String message) throws InterruptedException, ExecutionException {

        String stmtString = String.format(selectString, tableName, embeddingColumn, message);
        logger.debug(stmtString);
        logger.debug(message);

        CqlSession session = client.getStargateClient().initCqlSession();

        // TODO: Look into using prepared statement
        // see https://docs.datastax.com/en/developer/java-driver/4.17/upgrade_guide/ for Vector info
        // PreparedStatement ps = session.prepare(selectString);
        // CompletionStage<AsyncResultSet> rs = session.executeAsync(ps.bind(message));

        return session.executeAsync(stmtString);
    }

    //////////////////////////////////////////
    // Helper Methods
    //////////////////////////////////////////


}
