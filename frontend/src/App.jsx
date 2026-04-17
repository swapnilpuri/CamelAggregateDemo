import { usePipelineMetrics } from './hooks/usePipelineMetrics.js';
import StatsBar           from './components/StatsBar.jsx';
import PipelineFlow       from './components/PipelineFlow.jsx';
import ThroughputChart    from './components/ThroughputChart.jsx';
import RouteMetricsCards  from './components/RouteMetricsCards.jsx';
import SeedPanel          from './components/SeedPanel.jsx';
import FilteredQueryPanel from './components/FilteredQueryPanel.jsx';

export default function App() {
  const { metrics, connected, error, startPipeline, resetMetrics } = usePipelineMetrics();

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 p-4 flex flex-col gap-4">

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-50">
            🐫 Camel Banking ETL — Live Monitor
          </h1>
          <p className="text-sm text-slate-400">
            Oracle XE → Apache Camel 4 → Kinetica
          </p>
        </div>
        {error && (
          <div className="text-xs text-amber-400 bg-amber-900/30 border border-amber-700/40
                          rounded-lg px-3 py-1.5 max-w-xs">
            {error}
          </div>
        )}
      </div>

      {/* Stats + controls */}
      <StatsBar
        metrics={metrics}
        connected={connected}
        onStart={(limit) => startPipeline(limit)}
        onReset={resetMetrics}
      />

      {/* React Flow pipeline diagram */}
      <PipelineFlow metrics={metrics} />

      {/* Bottom panels */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">

        {/* Throughput chart — takes 2/3 width on xl */}
        <div className="xl:col-span-2">
          <ThroughputChart routes={metrics.routes} />
        </div>

        {/* Queue depths */}
        <div className="bg-slate-800 rounded-xl p-4 border border-slate-700 flex flex-col gap-3">
          <h3 className="text-sm font-semibold text-slate-300">SEDA Queue Depths</h3>
          {Object.values(metrics.queues ?? {}).map(q => {
            const pct = Math.min(q.percentFull ?? 0, 100);
            const barColor =
              pct > 80 ? 'bg-red-500' :
              pct > 50 ? 'bg-amber-400' :
              pct > 10 ? 'bg-blue-400' : 'bg-emerald-400';
            return (
              <div key={q.queueId} className="flex flex-col gap-1">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-300 font-medium">{q.queueId}</span>
                  <span className="text-slate-400 font-mono">
                    {q.currentDepth?.toLocaleString()} / {q.capacity?.toLocaleString()}
                  </span>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-2.5">
                  <div
                    className={`h-2.5 rounded-full transition-all duration-500 ${barColor}`}
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <div className="text-[10px] text-slate-500">
                  HWM: {q.highWaterMark?.toLocaleString()} &nbsp;·&nbsp; {pct.toFixed(1)}% full
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Per-route metric cards */}
      <RouteMetricsCards routes={metrics.routes} />

      {/* Data tools: seed + filtered query — side by side on xl */}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <SeedPanel />
        <FilteredQueryPanel />
      </div>

    </div>
  );
}
