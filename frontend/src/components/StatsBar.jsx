import { Play, RotateCcw, Wifi, WifiOff } from 'lucide-react';
import { useState } from 'react';

const STATUS_BADGE = {
  IDLE:      'bg-slate-700 text-slate-300',
  RUNNING:   'bg-blue-600  text-white animate-pulse',
  COMPLETED: 'bg-emerald-600 text-white',
};

const LIMIT_PRESETS = [
  { label: '1k',   value: 1_000 },
  { label: '10k',  value: 10_000 },
  { label: '100k', value: 100_000 },
  { label: 'All',  value: 0 },
];

function formatMs(ms) {
  if (!ms || ms < 1000) return `${ms ?? 0} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

function formatNum(n) {
  return (n ?? 0).toLocaleString();
}

export default function StatsBar({ metrics, connected, onStart, onReset }) {
  const [starting, setStarting]   = useState(false);
  const [limit, setLimit]         = useState(10_000);
  const [inputStr, setInputStr]   = useState('10000');

  const status = metrics?.pipelineStatus ?? 'IDLE';

  function handleInput(e) {
    const raw = e.target.value.replace(/[^0-9]/g, '');
    setInputStr(raw);
    const n = parseInt(raw, 10);
    if (!isNaN(n) && n >= 0) setLimit(n);
  }

  function applyPreset(value) {
    setLimit(value);
    setInputStr(value === 0 ? '0' : String(value));
  }

  async function handleStart() {
    setStarting(true);
    try { await onStart(limit); } catch (e) { alert(e.message); }
    finally { setStarting(false); }
  }

  return (
    <div className="flex flex-wrap items-center gap-3 px-4 py-3
                    bg-slate-800 border border-slate-700 rounded-xl shadow">

      {/* Pipeline status */}
      <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider
                        ${STATUS_BADGE[status] ?? STATUS_BADGE.IDLE}`}>
        {status}
      </span>

      {/* Elapsed */}
      <div className="flex flex-col items-center min-w-[70px]">
        <span className="text-xs text-slate-400">elapsed</span>
        <span className="font-mono font-bold text-slate-100">{formatMs(metrics?.elapsedMs)}</span>
      </div>

      <div className="h-8 w-px bg-slate-700" />

      {/* Record counters */}
      {[
        { label: 'fetched',     value: metrics?.recordsFetchedFromOracle,  color: 'text-orange-400' },
        { label: 'transformed', value: metrics?.recordsTransformed,         color: 'text-violet-400' },
        { label: 'inserted',    value: metrics?.recordsInsertedToKinetica,  color: 'text-emerald-400' },
      ].map(({ label, value, color }) => (
        <div key={label} className="flex flex-col items-center min-w-[70px]">
          <span className="text-xs text-slate-400">{label}</span>
          <span className={`font-mono font-bold ${color}`}>{formatNum(value)}</span>
        </div>
      ))}

      {/* Spacer */}
      <div className="flex-1" />

      {/* Record limit selector */}
      <div className="flex items-center gap-2 border border-slate-600 rounded-lg px-3 py-1.5">
        <span className="text-xs text-slate-400 whitespace-nowrap">Copy records:</span>

        {/* Preset buttons */}
        <div className="flex gap-1">
          {LIMIT_PRESETS.map(({ label, value }) => (
            <button
              key={label}
              onClick={() => applyPreset(value)}
              className={`px-2 py-0.5 rounded text-xs font-medium transition-colors
                ${limit === value
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-700 text-slate-300 hover:bg-slate-600'}`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Custom input */}
        <input
          type="text"
          inputMode="numeric"
          value={inputStr}
          onChange={handleInput}
          title="0 = all records"
          className="w-20 bg-slate-900 border border-slate-600 rounded px-2 py-0.5
                     text-xs text-slate-100 font-mono text-right
                     focus:outline-none focus:border-blue-500 transition-colors"
        />
        <span className="text-xs text-slate-500">{limit === 0 ? '(all)' : 'rows'}</span>
      </div>

      {/* Connection indicator */}
      <div className={`flex items-center gap-1.5 text-xs ${connected ? 'text-emerald-400' : 'text-red-400'}`}>
        {connected ? <Wifi size={14} /> : <WifiOff size={14} />}
        {connected ? 'Live' : 'Disconnected'}
      </div>

      {/* Controls */}
      <button
        onClick={onReset}
        title="Reset metrics"
        className="p-2 rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-300
                   hover:text-white transition-colors"
      >
        <RotateCcw size={15} />
      </button>

      <button
        onClick={handleStart}
        disabled={starting || status === 'RUNNING'}
        className="flex items-center gap-2 px-4 py-1.5 rounded-lg text-sm font-semibold
                   bg-blue-600 hover:bg-blue-500 text-white transition-colors
                   disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <Play size={14} />
        {starting ? 'Starting…' : 'Run Pipeline'}
      </button>
    </div>
  );
}
