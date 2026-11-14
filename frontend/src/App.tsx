import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { CaseDetailPanel } from './components/CaseDetailPanel';
import { CaseQueue } from './components/CaseQueue';
import type { CaseStatus, CaseSummary } from './types';

export default function App() {
  const [status, setStatus] = useState<CaseStatus | ''>('OPEN');
  const [cases, setCases] = useState<CaseSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [user, setUser] = useState('fcu.analyst');
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    api
      .listCases(status || undefined)
      .then((c) => {
        setCases(c);
        setError(null);
      })
      .catch((e: Error) => setError(e.message));
  }, [status]);

  useEffect(refresh, [refresh]);

  return (
    <div className="app">
      <header className="top">
        <h1>Ledgerline · FCU case queue</h1>
        <label>
          Analyst <input value={user} onChange={(e) => setUser(e.target.value)} aria-label="Analyst" />
        </label>
      </header>
      {error && <p role="alert" className="error">{error}</p>}
      <main>
        <CaseQueue cases={cases} status={status} selected={selected} onStatusChange={setStatus} onSelect={setSelected} />
        {selected ? (
          <CaseDetailPanel caseId={selected} user={user} onChanged={refresh} />
        ) : (
          <section className="panel">Select a case.</section>
        )}
      </main>
    </div>
  );
}
