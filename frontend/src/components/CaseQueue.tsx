import type { CaseStatus, CaseSummary } from '../types';

const STATUSES: (CaseStatus | '')[] = ['OPEN', 'REFER_FIELD_VERIFICATION', 'CLEARED', 'FRAUD_CONFIRMED', ''];

interface Props {
  cases: CaseSummary[];
  status: CaseStatus | '';
  selected: string | null;
  onStatusChange: (s: CaseStatus | '') => void;
  onSelect: (caseId: string) => void;
}

/** Queue of cases, already sorted highest score first by the API. */
export function CaseQueue({ cases, status, selected, onStatusChange, onSelect }: Props) {
  return (
    <section className="queue" aria-label="Case queue">
      <label>
        Status{' '}
        <select value={status} onChange={(e) => onStatusChange(e.target.value as CaseStatus | '')}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s === '' ? 'ALL' : s}
            </option>
          ))}
        </select>
      </label>
      <table>
        <thead>
          <tr>
            <th>Score</th>
            <th>Band</th>
            <th>Applicant</th>
            <th>File</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {cases.map((c) => (
            <tr
              key={c.caseId}
              className={c.caseId === selected ? 'selected' : undefined}
              onClick={() => onSelect(c.caseId)}
            >
              <td>{c.score}</td>
              <td>
                <span className={`band band-${c.band}`}>{c.band}</span>
              </td>
              <td>{c.applicantName}</td>
              <td>{c.fileId}</td>
              <td>{c.status}</td>
            </tr>
          ))}
          {cases.length === 0 && (
            <tr>
              <td colSpan={5}>No cases.</td>
            </tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
