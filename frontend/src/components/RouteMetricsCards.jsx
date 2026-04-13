const ROUTE_META = {
  fetchFromOracle:  { label: 'Fetch Oracle',    description: 'JDBC cursor → batchQueue',         color: 'border-orange-500/40' },
  processBatch:     { label: 'Process Batch',   description: 'batchQueue → individual records',   color: 'border-blue-500/40'   },
  transformRecord:  { label: 'Transform',       description: 'Per-record transform → insertQueue', color: 'border-violet-500/40' },
  insertToKinetica: { label: 'Insert Kinetica', description: 'Aggregate 10 000 → bulk INSERT',    color: 'border-emerald-500/40' },
};

const STATUS_DOT = {
  IDLE:      'bg-slate-500',
  RUNNING:   'bg-blue-400 animate-pulse',
  COMPLETED: 'bg-emerald-400',
  FAILED:    'bg-red-400',
};

function Stat({ label, value }) {
  return (
    <div className="flex justify-between items-baseline gap-2">
      <span className="text-xs text-slate-400">{label}</span>
      <span className="font-mono text-xs font-semibold text-slate-200">{value}</span>
    </div>
  );
}

function RouteCard({ routeMetric }) {
  const m    = routeMetric ?? {};
  const meta = ROUTE_META[m.routeId] ?? { label: m.routeId, description: '', color: 'border-slate-600' };

  return (
    <div className={`bg-slate-800 rounded-xl p-4 border-2 ${meta.color} flex flex-col gap-2`}>
      <div className="flex items-center gap-2">
        <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${STATUS_DOT[m.status ?? 'IDLE']}`} />
        <div>
          <div className="text-sm font-semibold text-slate-100">{meta.label}</div>
          <div className="text-[11px] text-slate-500">{meta.description}</div>
        </div>
      </div>

      <div className="border-t border-slate-700 pt-2 flex flex-col gap-1">
        <Stat label="Exchanges"  value={(m.exchangesTotal ?? 0).toLocaleString()} />
        <Stat label="Failed"     value={(m.exchangesFailed ?? 0).toLocaleString()} />
        <Stat label="Last ms"    value={m.lastProcessingTimeMs ? `${m.lastProcessingTimeMs} ms` : '—'} />
        <Stat label="Mean ms"    value={m.meanProcessingTimeMs ? `${m.meanProcessingTimeMs.toFixed(1)} ms` : '—'} />
        <Stat label="Throughput" value={m.throughputPerSec ? `${m.throughputPerSec.toFixed(1)} /s` : '—'} />
      </div>
    </div>
  );
}

export default function RouteMetricsCards({ routes }) {
  return (
    <div className="grid grid-cols-2 xl:grid-cols-4 gap-3">
      {['fetchFromOracle', 'processBatch', 'transformRecord', 'insertToKinetica'].map(id => (
        <RouteCard key={id} routeMetric={routes?.[id]} />
      ))}
    </div>
  );
}
