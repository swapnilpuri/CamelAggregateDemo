import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { useMemo } from 'react';

const ROUTE_COLORS = {
  fetchFromOracle:  '#f97316',   // orange
  processBatch:     '#3b82f6',   // blue
  transformRecord:  '#a78bfa',   // violet
  insertToKinetica: '#10b981',   // emerald
};

const ROUTE_LABELS = {
  fetchFromOracle:  'Fetch Oracle',
  processBatch:     'Process Batch',
  transformRecord:  'Transform',
  insertToKinetica: 'Insert Kinetica',
};

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-slate-800 border border-slate-600 rounded-lg p-3 text-xs shadow-xl">
      <p className="text-slate-400 mb-1.5">t−{label}×0.5s</p>
      {payload.map(p => (
        <div key={p.dataKey} className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full" style={{ background: p.color }} />
          <span className="text-slate-300">{ROUTE_LABELS[p.dataKey]}:</span>
          <span className="font-mono font-bold" style={{ color: p.color }}>
            {p.value.toFixed(1)} /s
          </span>
        </div>
      ))}
    </div>
  );
};

/**
 * Area chart showing per-route throughput over the last ~15 seconds.
 * metrics.routes[routeId].throughputHistory is an array of doubles (rec/s at each 500 ms tick).
 */
export default function ThroughputChart({ routes }) {
  const data = useMemo(() => {
    const histories = Object.fromEntries(
      Object.entries(routes ?? {}).map(([id, r]) => [id, r.throughputHistory ?? []])
    );
    const maxLen = Math.max(...Object.values(histories).map(h => h.length), 0);
    if (maxLen === 0) return [];

    return Array.from({ length: maxLen }, (_, i) => {
      const point = { tick: maxLen - i };
      Object.entries(histories).forEach(([id, hist]) => {
        const idx = hist.length - maxLen + i;
        point[id] = idx >= 0 ? +(hist[idx] ?? 0).toFixed(2) : 0;
      });
      return point;
    });
  }, [routes]);

  return (
    <div className="bg-slate-800 rounded-xl p-4 border border-slate-700">
      <h3 className="text-sm font-semibold text-slate-300 mb-3">
        Throughput per Route&nbsp;
        <span className="font-normal text-slate-500 text-xs">(exchanges / s, 500 ms ticks)</span>
      </h3>
      <ResponsiveContainer width="100%" height={180}>
        <AreaChart data={data} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
          <defs>
            {Object.entries(ROUTE_COLORS).map(([id, color]) => (
              <linearGradient key={id} id={`grad-${id}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%"  stopColor={color} stopOpacity={0.3} />
                <stop offset="95%" stopColor={color} stopOpacity={0}   />
              </linearGradient>
            ))}
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
          <XAxis dataKey="tick" tick={{ fontSize: 10, fill: '#64748b' }} reversed />
          <YAxis tick={{ fontSize: 10, fill: '#64748b' }} />
          <Tooltip content={<CustomTooltip />} />
          <Legend
            wrapperStyle={{ fontSize: 11, paddingTop: 8 }}
            formatter={(value) => (
              <span style={{ color: ROUTE_COLORS[value] }}>{ROUTE_LABELS[value]}</span>
            )}
          />
          {Object.entries(ROUTE_COLORS).map(([id, color]) => (
            <Area
              key={id}
              type="monotone"
              dataKey={id}
              stroke={color}
              strokeWidth={2}
              fill={`url(#grad-${id})`}
              dot={false}
              isAnimationActive={false}
            />
          ))}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
