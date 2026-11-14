import type { RuleHit } from '../types';

const money = (v: unknown) =>
  typeof v === 'number' ? v.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : String(v);

/** Renders each rule's evidence in analyst-friendly form, falling back to key/value pairs. */
export function EvidenceView({ hit }: { hit: RuleHit }) {
  const e = hit.evidence;
  switch (hit.ruleId) {
    case 'BALANCE_BREAK':
      return (
        <p>
          Row {String(e.rowIndex)} on {String(e.date)}: expected balance <strong>₹{money(e.expectedBalance)}</strong>,
          statement shows <strong>₹{money(e.statedBalance)}</strong>
        </p>
      );
    case 'SALARY_CREDIT_MISSING':
      return <p>Unmatched salary months: {(e.unmatchedMonths as string[]).join(', ')}</p>;
    case 'DECLARED_INCOME_ABOVE_SLIPS':
      return (
        <p>
          Declared ₹{money(e.declaredMonthlyIncome)} vs average slip net pay ₹{money(e.averageSlipNetPay)} (limit{' '}
          {String(e.maxRatio)}×)
        </p>
      );
    case 'DOC_METADATA_SUSPECT':
      return (
        <ul>
          {(e.documents as { docId: string; failedChecks: string[] }[]).map((d) => (
            <li key={d.docId}>
              {d.docId}: {d.failedChecks.join(', ')}
            </li>
          ))}
        </ul>
      );
    case 'NAME_MISMATCH':
      return (
        <ul>
          {(e.mismatches as { docId: string; field: string; value: string }[]).map((m) => (
            <li key={m.docId + m.field}>
              {m.docId} {m.field}: “{m.value}” (applicant “{String(e.applicantName)}”)
            </li>
          ))}
        </ul>
      );
    case 'DOCUMENT_REUSED':
      return (
        <p>
          {String(e.docId)} matched by {String(e.matchedHash)} on files {(e.otherFileIds as string[]).join(', ')}
        </p>
      );
    default:
      return <pre>{JSON.stringify(e, null, 2)}</pre>;
  }
}
