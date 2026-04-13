import { useState, useEffect, useRef, useCallback } from 'react';

const EMPTY_METRICS = {
  pipelineStatus: 'IDLE',
  startTimeMs: 0,
  elapsedMs: 0,
  recordsFetchedFromOracle: 0,
  recordsTransformed: 0,
  recordsInsertedToKinetica: 0,
  routes: {
    fetchFromOracle:  { routeId: 'fetchFromOracle',  status: 'IDLE', exchangesTotal: 0, exchangesFailed: 0, lastProcessingTimeMs: 0, meanProcessingTimeMs: 0, throughputPerSec: 0, throughputHistory: [] },
    processBatch:     { routeId: 'processBatch',     status: 'IDLE', exchangesTotal: 0, exchangesFailed: 0, lastProcessingTimeMs: 0, meanProcessingTimeMs: 0, throughputPerSec: 0, throughputHistory: [] },
    transformRecord:  { routeId: 'transformRecord',  status: 'IDLE', exchangesTotal: 0, exchangesFailed: 0, lastProcessingTimeMs: 0, meanProcessingTimeMs: 0, throughputPerSec: 0, throughputHistory: [] },
    insertToKinetica: { routeId: 'insertToKinetica', status: 'IDLE', exchangesTotal: 0, exchangesFailed: 0, lastProcessingTimeMs: 0, meanProcessingTimeMs: 0, throughputPerSec: 0, throughputHistory: [] },
  },
  queues: {
    batchQueue:     { queueId: 'batchQueue',     currentDepth: 0, capacity: 200,  highWaterMark: 0, percentFull: 0 },
    transformQueue: { queueId: 'transformQueue', currentDepth: 0, capacity: 5000, highWaterMark: 0, percentFull: 0 },
    insertQueue:    { queueId: 'insertQueue',    currentDepth: 0, capacity: 5000, highWaterMark: 0, percentFull: 0 },
  },
};

export function usePipelineMetrics() {
  const [metrics, setMetrics]     = useState(EMPTY_METRICS);
  const [connected, setConnected] = useState(false);
  const [error, setError]         = useState(null);
  const esRef = useRef(null);

  const connect = useCallback(() => {
    if (esRef.current) esRef.current.close();

    const es = new EventSource('/api/pipeline/stream');
    esRef.current = es;

    es.addEventListener('metrics', (e) => {
      try {
        setMetrics(JSON.parse(e.data));
        setConnected(true);
        setError(null);
      } catch (err) {
        setError('Failed to parse metrics data');
      }
    });

    es.onerror = () => {
      setConnected(false);
      setError('Lost connection to server — retrying…');
    };
  }, []);

  useEffect(() => {
    connect();
    return () => esRef.current?.close();
  }, [connect]);

  const startPipeline = useCallback(async (limit = 0) => {
    const url = limit > 0
      ? `/api/pipeline/start?limit=${limit}`
      : '/api/pipeline/start';
    const res = await fetch(url, { method: 'POST' });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || 'Failed to start pipeline');
    }
  }, []);

  const resetMetrics = useCallback(async () => {
    await fetch('/api/pipeline/reset', { method: 'POST' });
  }, []);

  return { metrics, connected, error, startPipeline, resetMetrics };
}
