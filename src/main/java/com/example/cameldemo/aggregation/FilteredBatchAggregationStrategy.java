package com.example.cameldemo.aggregation;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aggregation strategy for the filteredAggregateAndLoad route.
 *
 * Accumulates individual Oracle result rows (Map&lt;String,Object&gt;) received from
 * seda:filteredInsertQueue into a single List&lt;Map&lt;String,Object&gt;&gt;.  The Camel
 * aggregate EIP fires the completion callback when either:
 *   • completionSize (20 000) rows have been collected, or
 *   • completionTimeout (5 s) elapses with no new row (flushes the final partial batch).
 *
 * The completed List is then passed to {@code kineticaInsertBean.insertBatch()}.
 */
@Component("filteredBatchAggregationStrategy")
public class FilteredBatchAggregationStrategy implements AggregationStrategy {

    @Override
    @SuppressWarnings("unchecked")
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Map<String, Object> newRow = newExchange.getMessage().getBody(Map.class);

        if (oldExchange == null) {
            // First row in this batch — create the accumulator list
            List<Map<String, Object>> batch = new ArrayList<>();
            batch.add(newRow);
            newExchange.getMessage().setBody(batch);
            return newExchange;
        }

        // Subsequent rows — append to the existing accumulator
        List<Map<String, Object>> batch = oldExchange.getMessage().getBody(List.class);
        batch.add(newRow);
        return oldExchange;
    }
}
