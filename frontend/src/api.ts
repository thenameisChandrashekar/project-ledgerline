import type { AuditEntry, CaseDetail, CaseStatus, CaseSummary, Disposition } from './types';

export class ApiError extends Error {}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(body.message ?? `Request failed with status ${response.status}`);
  }
  return body as T;
}

export const api = {
  listCases: (status?: CaseStatus) =>
    request<CaseSummary[]>(`/api/cases${status ? `?status=${status}` : ''}`),
  getCase: (caseId: string) => request<CaseDetail>(`/api/cases/${caseId}`),
  getAudit: (caseId: string) => request<AuditEntry[]>(`/api/cases/${caseId}/audit`),
  dispose: (caseId: string, disposition: Disposition, comment: string, user: string) =>
    request<CaseDetail>(`/api/cases/${caseId}/disposition`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User': user },
      body: JSON.stringify({ disposition, comment }),
    }),
};
