import { Handle, Position } from 'reactflow';
import { Database } from 'lucide-react';

/**
 * Represents Oracle (source) or Kinetica (target) in the pipeline diagram.
 * data: { label, recordCount, sublabel, isSource }
 */
export default function DbNode({ data }) {
  const { label, recordCount, sublabel, isSource } = data;
  return (
    <div className="flex flex-col items-center gap-1 px-4 py-3
                    bg-slate-800 border-2 border-slate-600 rounded-xl
                    min-w-[120px] shadow-lg shadow-black/30">
      {!isSource && <Handle type="target" position={Position.Left} className="!bg-blue-400" />}

      <Database size={28} className={isSource ? 'text-orange-400' : 'text-emerald-400'} />
      <span className="text-slate-100 font-semibold text-sm leading-tight text-center">
        {label}
      </span>
      <span className="text-2xl font-bold tabular-nums text-slate-50">
        {recordCount?.toLocaleString() ?? '—'}
      </span>
      <span className="text-xs text-slate-400">{sublabel}</span>

      {isSource && <Handle type="source" position={Position.Right} className="!bg-blue-400" />}
    </div>
  );
}
