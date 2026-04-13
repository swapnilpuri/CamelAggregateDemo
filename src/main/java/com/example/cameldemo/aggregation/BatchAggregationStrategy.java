package com.example.cameldemo.aggregation;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aggregates individual transformed rows into a List for bulk insertion into Kinetica.
 *
 * Used in Route 4: seda:insertQueue → aggregate(completionSize=10000) → Kinetica bulk insert.
 *
 * The aggregated body (List<Map<String,Object>>) is handed to the Kinetica JDBC component,
 * which issues a single prepared-statement batch INSERT for the entire list.
 */
@Component("batchAggregationStrategy")
public class BatchAggregationStrategy implements AggregationStrategy {

    private static final Logger log = LoggerFactory.getLogger(BatchAggregationStrategy.class);

    @Override
    @SuppressWarnings("unchecked")
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Map<String, Object> newRow = newExchange.getIn().getBody(Map.class);

        if (oldExchange == null) {
            // First record in this aggregation window — initialise the list
            List<Map<String, Object>> batch = new ArrayList<>();
            if (newRow != null) batch.add(newRow);
            newExchange.getIn().setBody(batch);
            return newExchange;
        }

        List<Map<String, Object>> batch = oldExchange.getIn().getBody(List.class);
        if (batch == null) {
            batch = new ArrayList<>();
            oldExchange.getIn().setBody(batch);
        }
        if (newRow != null) {
            batch.add(newRow);
        }

        log.trace("Aggregated batch now contains {} records", batch.size());
        return oldExchange;
    }
}
