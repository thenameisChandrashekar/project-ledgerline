export type CaseStatus = 'OPEN' | 'REFER_FIELD_VERIFICATION' | 'CLEARED' | 'FRAUD_CONFIRMED';
export type Disposition = 'CLEARED' | 'REFER_FIELD_VERIFICATION' | 'FRAUD_CONFIRMED';

export interface CaseSummary {
  caseId: string;
  fileId: string;
  applicantName: string;
  panMasked: string;
  score: number;
  band: string;
  status: CaseStatus;
  createdAt: string;
}

export interface RuleHit {
  ruleId: string;
  points: number;
  reason: string;
  evidence: Record<string, unknown>;
}

export interface CaseDetail extends Omit<CaseSummary, 'status'> {
  status: CaseStatus;
  accountNumberMasked: string;
  updatedAt: string;
  ruleHits: RuleHit[];
  degradedSignals: string[];
}

export interface AuditEntry {
  caseId: string;
  fromStatus: CaseStatus;
  toStatus: CaseStatus;
  actor: string;
  comment: string;
  at: string;
}
