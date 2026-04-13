import { useState, useEffect, useCallback } from 'react';
import { DatabaseZap, Loader2, CheckCircle2, AlertCircle } from 'lucide-react';

const PRESETS = [100, 1_000, 10_000, 100_000];

function formatNum(n) { return (n ?? 0).toLocaleString(); }
function formatMs(ms) {
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

export default function SeedPanel() {
  const [count,       setCount]       = useState(1000);
  const [inputStr,    setInputStr]    = useState('1000');
  const [seeding,     setSeeding]     = useState(false);
  const [result,      setResult]      = useState(null);   // SeederResult | null
  const [error,       setError]       = useState(null);
  const [oracleRows,  setOracleRows]  = useState(null);

  // Fetch current Oracle row count on mount and after each seed
  const refreshCount = useCallback(async () => {
    try {
      const res = await fetch('/api/seed/count');
      if (res.ok) setOracleRows(await res.json());
    } catch { /* ignore — Spring Boot may not be up yet */ }
  }, []);

  useEffect(() => { refreshCount(); }, [refreshCount]);

  function handleInput(e) {
    const raw = e.target.value.replace(/[^0-9]/g, '');
    setInputStr(raw);
    const n = parseInt(raw, 10);
    if (!isNaN(n) && n > 0) setCount(n);
  }

  function applyPreset(n) {
    setCount(n);
    setInputStr(n.toLocaleString().replace(/,/g, ''));
  }

  async function handleSeed() {
    setSeeding(true);
    setResult(null);
    setError(null);
    try {
      const res = await fetch(`/api/seed?count=${count}`, { method: 'POST' });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      setResult(data);
      setOracleRows(data.oracleTotalRows);
    } catch (e) {
      setError(e.message);
    } finally {
      setSeeding(false);
    }
  }

  return (
    <div className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex flex-col gap-4">

      {/* Header */}
      <div className="flex items-center gap-2">
        <DatabaseZap size={18} className="text-amber-400" />
        <h3 className="text-sm font-semibold text-slate-200">Seed Oracle Data</h3>
        {oracleRows !== null && (
          <span className="ml-auto text-xs text-slate-400">
            Current rows:&nbsp;
            <span className="font-mono font-semibold text-slate-200">{formatNum(oracleRows)}</span>
          </span>
        )}
      </div>

      {/* Preset buttons */}
      <div className="flex flex-wrap gap-2">
        {PRESETS.map(n => (
          <button
            key={n}
            onClick={() => applyPreset(n)}
            className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors
              ${count === n
                ? 'bg-amber-600 text-white'
                : 'bg-slate-700 text-slate-300 hover:bg-slate-600'}`}
          >
            {n.toLocaleString()}
          </button>
        ))}
      </div>

      {/* Custom count input + action button */}
      <div className="flex items-center gap-2">
        <div className="flex-1 relative">
          <input
            type="text"
            inputMode="numeric"
            value={inputStr}
            onChange={handleInput}
            placeholder="Custom count…"
            className="w-full bg-slate-900 border border-slate-600 rounded-lg px-3 py-2
                       text-sm text-slate-100 placeholder-slate-500 font-mono
                       focus:outline-none focus:border-amber-500 transition-colors"
          />
        </div>
        <button
          onClick={handleSeed}
          disabled={seeding || !count || count < 1}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold
                     bg-amber-600 hover:bg-amber-500 text-white transition-colors
                     disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
        >
          {seeding
            ? <><Loader2 size={14} className="animate-spin" /> Seeding…</>
            : <><DatabaseZap size={14} /> Seed {formatNum(count)} rows</>
          }
        </button>
      </div>

      {/* Result */}
      {result && !error && (
        <div className="bg-emerald-950/50 border border-emerald-700/40 rounded-lg p-3
                        flex flex-col gap-1 text-xs">
          <div className="flex items-center gap-1.5 text-emerald-400 font-semibold mb-1">
            <CheckCircle2 size={13} />
            Inserted {formatNum(result.insertedCount)} rows
          </div>
          <div className="grid grid-cols-2 gap-x-4 gap-y-0.5 text-slate-300">
            <span className="text-slate-400">Duration</span>
            <span className="font-mono">{formatMs(result.durationMs)}</span>
            <span className="text-slate-400">Throughput</span>
            <span className="font-mono">{formatNum(Math.round(result.recordsPerSecond))} rows/s</span>
            <span className="text-slate-400">Oracle total</span>
            <span className="font-mono">{formatNum(result.oracleTotalRows)}</span>
          </div>
        </div>
      )}

      {error && (
        <div className="bg-red-950/50 border border-red-700/40 rounded-lg p-3
                        flex items-start gap-2 text-xs text-red-300">
          <AlertCircle size={13} className="mt-0.5 flex-shrink-0 text-red-400" />
          {error}
        </div>
      )}
    </div>
  );
}
