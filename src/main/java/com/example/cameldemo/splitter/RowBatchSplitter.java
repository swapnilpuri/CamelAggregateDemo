package com.example.cameldemo.splitter;

import org.apache.camel.Body;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Splits a streaming JDBC result (Iterator of rows) into fixed-size batches.
 *
 * Used in Route 1: the JDBC component with outputType=StreamList returns an Iterator.
 * This bean groups rows into List<Map<String,Object>> batches of configurable size,
 * which are then sent individually to seda:batchQueue.
 */
@Component
public class RowBatchSplitter {

    private static final Logger log = LoggerFactory.getLogger(RowBatchSplitter.class);

    @Value("${camel.batch-size:1000}")
    private int batchSize;

    /**
     * Returns an Iterable of batches. Camel's split EIP will iterate over it,
     * sending each batch (List<Map<String,Object>>) as a separate exchange body.
     */
    @SuppressWarnings("unchecked")
    public Iterable<List<Map<String, Object>>> createBatches(@Body Object body) {
        Iterator<Map<String, Object>> rows = (Iterator<Map<String, Object>>) body;
        return () -> new BatchIterator(rows, batchSize);
    }

    private static class BatchIterator implements Iterator<List<Map<String, Object>>> {

        private final Iterator<Map<String, Object>> source;
        private final int size;
        private long totalRows = 0;
        private int batchCount = 0;

        BatchIterator(Iterator<Map<String, Object>> source, int size) {
            this.source = source;
            this.size = size;
        }

        @Override
        public boolean hasNext() {
            return source.hasNext();
        }

        @Override
        public List<Map<String, Object>> next() {
            List<Map<String, Object>> batch = new ArrayList<>(size);
            while (source.hasNext() && batch.size() < size) {
                batch.add(source.next());
            }
            totalRows += batch.size();
            batchCount++;
            log.debug("Produced batch #{} with {} rows ({} total rows so far)",
                    batchCount, batch.size(), totalRows);
            return batch;
        }
    }
}
