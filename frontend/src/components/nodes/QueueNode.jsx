import { Handle, Position } from 'reactflow';

const QUEUE_LABELS = {
  batchQueue:     'Batch\nQueue',
  transformQueue: 'Transform\nQueue',
  insertQueue:    'Insert\nQueue',
};

/** Fill colour based on queue saturation */
function fillColor(pct) {
  if (pct > 80) return 'bg-red-500';
  if (pct > 50) return 'bg-amber-400';
  if (pct > 20) return 'bg-blue-400';
  return 'bg-emerald-400';
}

/**
 * Represents a SEDA queue between two routes.
 * data: { queueMetric }  (QueueMetricSnapshot)
 */
export default function QueueNode({ data }) {
  const q   = data.queueMetric ?? {};
  const pct = Math.min(q.percentFull ?? 0, 100);

  return (
    <div className="flex flex-col items-center gap-1.5 px-3 py-2.5
                    bg-slate-800 border-2 border-slate-600 rounded-xl
                    min-w-[90px] shadow-lg shadow-black/30">
      <Handle type="target" position={Position.Left}  className="!bg-blue-400" />
      <Handle type="source" position={Position.Right} className="!bg-blue-400" />

      <span className="text-[10px] font-semibold text-slate-300 whitespace-pre-line leading-tight text-center">
        {QUEUE_LABELS[q.queueId] ?? q.queueId}
      </span>

      {/* Depth gauge */}
      <div className="w-full bg-slate-700 rounded-full h-2 overflow-hidden">
        <div
          className={`h-2 rounded-full transition-all duration-500 ${fillColor(pct)}`}
          style={{ width: `${pct}%` }}
        />
      </div>

      <div className="text-[9px] text-slate-400 tabular-nums">
        {(q.currentDepth ?? 0).toLocaleString()} / {(q.capacity ?? 0).toLocaleString()}
      </div>
      <div className="text-[9px] text-slate-500">
        HWM: {(q.highWaterMark ?? 0).toLocaleString()}
      </div>
    </div>
  );
}
