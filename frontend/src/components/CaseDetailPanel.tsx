import { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import type { AuditEntry, CaseDetail, Disposition } from '../types';
import { EvidenceView } from './EvidenceView';

const ACTIONS: Record<string, Disposition[]> = {
  OPEN: ['CLEARED', 'REFER_FIELD_VERIFICATION', 'FRAUD_CONFIRMED'],
  REFER_FIELD_VERIFICATION: ['CLEARED', 'FRAUD_CONFIRMED'],
};

interface Props {
  caseId: string;
  user: string;
  onChanged?: () => void;
}

export function CaseDetailPanel({ caseId, user, onChanged }: Props) {
  const [detail, setDetail] = useState<CaseDetail | null>(null);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [d, a] = await Promise.all([api.getCase(caseId), api.getAudit(caseId)]);
      setDetail(d);
      setAudit(a);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [caseId]);

  useEffect(() => {
    setError(null);
    setComment('');
    void load();
  }, [load]);

  const dispose = async (disposition: Disposition) => {
    setError(null);
    try {
      await api.dispose(caseId, disposition, comment, user);
      setComment('');
      await load();
      onChanged?.();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  if (!detail) {
    return <section className="panel">{error ? <p role="alert">{error}</p> : 'Loading…'}</section>;
  }

  return (
    <section className="panel" aria-label="Case detail">
      <h2>
        {detail.applicantName} <span className={`band band-${detail.band}`}>{detail.band}</span>
      </h2>
      <dl className="facts">
        <dt>File</dt>
        <dd>{detail.fileId}</dd>
        <dt>PAN</dt>
        <dd>{detail.panMasked}</dd>
        <dt>Account</dt>
        <dd>{detail.accountNumberMasked}</dd>
        <dt>Score</dt>
        <dd>{detail.score}</dd>
        <dt>Status</dt>
        <dd>{detail.status}</dd>
      </dl>
      {detail.degradedSignals.length > 0 && (
        <p className="warn">Not fully checked: {detail.degradedSignals.join(', ')} was unavailable.</p>
      )}

      <h3>Rule hits</h3>
      {detail.ruleHits.length === 0 && <p>No rules fired.</p>}
      {detail.ruleHits.map((hit) => (
        <article key={hit.ruleId} className="hit">
          <header>
            <strong>{hit.ruleId}</strong> <span>+{hit.points}</span>
          </header>
          <p className="reason">{hit.reason}</p>
          <EvidenceView hit={hit} />
        </article>
      ))}

      {ACTIONS[detail.status] && (
        <>
          <h3>Disposition</h3>
          <textarea
            aria-label="Comment"
            placeholder="Comment (at least 10 characters)"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          <div className="actions">
            {ACTIONS[detail.status].map((d) => (
              <button key={d} onClick={() => void dispose(d)}>
                {d.replace(/_/g, ' ')}
              </button>
            ))}
          </div>
        </>
      )}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      <h3>Audit history</h3>
      {audit.length === 0 ? (
        <p>No decisions yet.</p>
      ) : (
        <ol className="audit">
          {audit.map((a, i) => (
            <li key={i}>
              {new Date(a.at).toLocaleString()} — <strong>{a.actor}</strong>: {a.fromStatus} → {a.toStatus} — “
              {a.comment}”
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
