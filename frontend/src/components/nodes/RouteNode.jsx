import { Handle, Position } from 'reactflow';

const STATUS_STYLES = {
  IDLE:      'border-slate-600  bg-slate-800  text-slate-400',
  RUNNING:   'border-blue-500   bg-blue-950   text-blue-300',
  COMPLETED: 'border-emerald-500 bg-emerald-950 text-emerald-300',
  FAILED:    'border-red-500    bg-red-950    text-red-300',
};

const STATUS_DOT = {
  IDLE:      'bg-slate-500',
  RUNNING:   'bg-blue-400 animate-pulse',
  COMPLETED: 'bg-emerald-400',
  FAILED:    'bg-red-400',
};

const ROUTE_LABELS = {
  fetchFromOracle:  'Fetch\nOracle',
  processBatch:     'Process\nBatch',
  transformRecord:  'Transform\nRecord',
  insertToKinetica: 'Insert\nKinetica',
};

/**
 * Represents a Camel route step.
 * data: { routeMetric }  (RouteMetricSnapshot)
 */
export default function RouteNode({ data }) {
  const m      = data.routeMetric ?? {};
  const status = m.status ?? 'IDLE';
  const label  = ROUTE_LABELS[m.routeId] ?? m.routeId ?? '';

  return (
    <div className={`flex flex-col gap-1.5 px-3 py-2.5
                     border-2 rounded-xl min-w-[110px] shadow-lg shadow-black/30
                     transition-colors duration-300 ${STATUS_STYLES[status]}`}>
      <Handle type="target" position={Position.Left}  className="!bg-blue-400" />
      <Handle type="source" position={Position.Right} className="!bg-blue-400" />

      {/* Header */}
      <div className="flex items-center gap-1.5">
        <span className={`w-2 h-2 rounded-full flex-shrink-0 ${STATUS_DOT[status]}`} />
        <span className="text-xs font-semibold whitespace-pre-line leading-tight">
          {label}
        </span>
      </div>

      {/* Metrics grid */}
      <div className="grid grid-cols-2 gap-x-2 gap-y-0.5 text-[10px]">
        <span className="text-slate-400">processed</span>
        <span className="font-mono font-bold tabular-nums">
          {(m.exchangesTotal ?? 0).toLocaleString()}
        </span>

        <span className="text-slate-400">avg ms</span>
        <span className="font-mono tabular-nums">
          {m.meanProcessingTimeMs ? m.meanProcessingTimeMs.toFixed(0) : '—'}
        </span>

        <span className="text-slate-400">rec/s</span>
        <span className="font-mono tabular-nums">
          {m.throughputPerSec ? m.throughputPerSec.toFixed(1) : '—'}
        </span>

        {(m.exchangesFailed ?? 0) > 0 && (
          <>
            <span className="text-red-400">failed</span>
            <span className="font-mono text-red-400">{m.exchangesFailed}</span>
          </>
        )}
      </div>
    </div>
  );
}
