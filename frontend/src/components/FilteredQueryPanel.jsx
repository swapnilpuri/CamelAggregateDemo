import { useState } from 'react';
import {
  Filter, Play, X, Loader2, CheckCircle2, AlertCircle,
} from 'lucide-react';

// ── Preset chips shown below each string filter ────────────────────────────
const CURRENCY_CHIPS = ['USD', 'EUR', 'GBP', 'JPY', 'INR', 'CAD'];
const TYPE_CHIPS     = ['DEBIT', 'CREDIT', 'TRANSFER', 'PAYMENT'];
const STATUS_CHIPS   = ['PENDING', 'COMPLETED', 'FAILED'];

const EMPTY_FILTERS  = {
  amount: '', currency: '', transactionType: '', status: '', branchCode: '',
};

// ── Reusable chip row ───────────────────────────────────────────────────────
function ChipRow({ options, value, onChange }) {
  return (
    <div className="flex flex-wrap gap-1 mt-1.5">
      {options.map(opt => (
        <button
          key={opt}
          type="button"
          onClick={() => onChange(value === opt ? '' : opt)}
          className={`px-2 py-0.5 rounded text-[10px] font-semibold tracking-wide
                      transition-colors
            ${value === opt
              ? 'bg-violet-600 text-white'
              : 'bg-slate-700 text-slate-400 hover:bg-slate-600 hover:text-slate-200'}`}
        >
          {opt}
        </button>
      ))}
    </div>
  );
}

// ── Labelled field wrapper ─────────────────────────────────────────────────
function Field({ label, hint, children }) {
  return (
    <div className="flex flex-col gap-0.5">
      <div className="flex items-baseline gap-1.5">
        <span className="text-xs font-medium text-slate-300">{label}</span>
        {hint && (
          <span className="text-[10px] text-slate-500">{hint}</span>
        )}
      </div>
      {children}
    </div>
  );
}

// ── Shared text-input class ────────────────────────────────────────────────
const inputCls =
  'bg-slate-900 border border-slate-600 rounded-lg px-3 py-2 w-full ' +
  'text-sm text-slate-100 placeholder-slate-600 font-mono ' +
  'focus:outline-none focus:border-violet-500 transition-colors';

// ══════════════════════════════════════════════════════════════════════════
export default function FilteredQueryPanel() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [loading, setLoading] = useState(false);
  const [result,  setResult]  = useState(null);
  const [error,   setError]   = useState(null);

  // ── Helpers ──────────────────────────────────────────────────────────────
  function setField(key, value) {
    setFilters(f => ({ ...f, [key]: value }));
    setResult(null);
    setError(null);
  }

  function clearAll() {
    setFilters(EMPTY_FILTERS);
    setResult(null);
    setError(null);
  }

  const activeCount = Object.values(filters).filter(v => v !== '').length;

  // ── Submit ───────────────────────────────────────────────────────────────
  async function handleRun() {
    setLoading(true);
    setResult(null);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (filters.amount.trim())          params.set('amount',          filters.amount.trim());
      if (filters.currency.trim())        params.set('currency',        filters.currency.trim());
      if (filters.transactionType.trim()) params.set('transactionType', filters.transactionType.trim());
      if (filters.status.trim())          params.set('status',          filters.status.trim());
      if (filters.branchCode.trim())      params.set('branchCode',      filters.branchCode.trim());

      const qs  = params.toString();
      const res = await fetch(`/api/query/filtered${qs ? `?${qs}` : ''}`, { method: 'POST' });
      if (!res.ok) throw new Error(await res.text() || `HTTP ${res.status}`);
      setResult(await res.json());
    } catch (e) {
      setError(e.message || 'Request failed');
    } finally {
      setLoading(false);
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex flex-col gap-4">

      {/* ── Header ── */}
      <div className="flex items-center gap-2">
        <Filter size={16} className="text-violet-400 shrink-0" />
        <h3 className="text-sm font-semibold text-slate-200">
          Filtered Oracle → Kinetica Query
        </h3>
        {activeCount > 0 && (
          <span className="ml-1 px-2 py-0.5 rounded-full text-[10px] font-bold
                           bg-violet-900/60 text-violet-300 border border-violet-700/50">
            {activeCount} filter{activeCount !== 1 ? 's' : ''} active
          </span>
        )}
      </div>

      {/* ── Filter fields — 2-column grid on md+ ── */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-5">

        {/* AMOUNT */}
        <Field label="Min. Amount" hint="(≥ value)">
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2
                             text-slate-400 text-xs select-none pointer-events-none">≥</span>
            <input
              type="number"
              min="0"
              step="any"
              value={filters.amount}
              onChange={e => setField('amount', e.target.value)}
              placeholder="e.g. 500"
              className={`${inputCls} pl-7`}
            />
          </div>
        </Field>

        {/* CURRENCY */}
        <Field label="Currency" hint="(exact match)">
          <input
            type="text"
            value={filters.currency}
            onChange={e => setField('currency', e.target.value.toUpperCase())}
            placeholder="e.g. USD"
            maxLength={10}
            className={inputCls}
          />
          <ChipRow
            options={CURRENCY_CHIPS}
            value={filters.currency}
            onChange={v => setField('currency', v)}
          />
        </Field>

        {/* TRANSACTION TYPE */}
        <Field label="Transaction Type" hint="(exact match)">
          <input
            type="text"
            value={filters.transactionType}
            onChange={e => setField('transactionType', e.target.value.toUpperCase())}
            placeholder="e.g. DEBIT"
            maxLength={30}
            className={inputCls}
          />
          <ChipRow
            options={TYPE_CHIPS}
            value={filters.transactionType}
            onChange={v => setField('transactionType', v)}
          />
        </Field>

        {/* STATUS */}
        <Field label="Status" hint="(exact match)">
          <input
            type="text"
            value={filters.status}
            onChange={e => setField('status', e.target.value.toUpperCase())}
            placeholder="e.g. COMPLETED"
            maxLength={20}
            className={inputCls}
          />
          <ChipRow
            options={STATUS_CHIPS}
            value={filters.status}
            onChange={v => setField('status', v)}
          />
        </Field>

        {/* BRANCH CODE — spans both columns on md+ */}
        <div className="md:col-span-2">
          <Field label="Branch Code" hint="(exact match)">
            <input
              type="text"
              value={filters.branchCode}
              onChange={e => setField('branchCode', e.target.value.toUpperCase())}
              placeholder="e.g. BRANCH-001"
              maxLength={20}
              className={inputCls}
            />
          </Field>
        </div>

      </div>

      {/* ── Action row ── */}
      <div className="flex flex-wrap items-center gap-2">
        <button
          onClick={handleRun}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold
                     bg-violet-600 hover:bg-violet-500 text-white transition-colors
                     disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading
            ? <><Loader2 size={14} className="animate-spin" /> Running…</>
            : <><Play    size={14} /> Run Filtered Query</>}
        </button>

        {activeCount > 0 && !loading && (
          <button
            onClick={clearAll}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium
                       bg-slate-700 hover:bg-slate-600 text-slate-300 transition-colors"
          >
            <X size={13} /> Clear filters
          </button>
        )}

        <span className="ml-auto text-[11px] text-slate-500 italic">
          Runs async · monitor server logs for progress
        </span>
      </div>

      {/* ── Success banner ── */}
      {result && !error && (
        <div className="bg-violet-950/50 border border-violet-700/40 rounded-lg p-3
                        flex flex-col gap-2 text-xs">
          <div className="flex items-center gap-1.5 text-violet-300 font-semibold">
            <CheckCircle2 size={13} />
            {result.message}
          </div>

          {result.activeFilters && Object.keys(result.activeFilters).length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-x-6 gap-y-1 pl-1 pt-1">
              {Object.entries(result.activeFilters).map(([k, v]) => (
                <div key={k} className="flex items-center gap-1.5">
                  <span className="text-slate-500 capitalize">{k}</span>
                  <span className="font-mono text-slate-200">{String(v)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── Error banner ── */}
      {error && (
        <div className="bg-red-950/50 border border-red-700/40 rounded-lg p-3
                        flex items-start gap-2 text-xs text-red-300">
          <AlertCircle size={13} className="mt-0.5 shrink-0 text-red-400" />
          {error}
        </div>
      )}
    </div>
  );
}
