import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CaseDetailPanel } from './CaseDetailPanel';

const detail = {
  caseId: 'c-1',
  fileId: 'LF-00042',
  applicantName: 'Asha Rao',
  panMasked: '******234F',
  accountNumberMasked: 'XXXXXXXX4821',
  score: 50,
  band: 'REFER',
  status: 'OPEN',
  createdAt: '2026-11-14T06:40:00Z',
  updatedAt: '2026-11-14T06:40:00Z',
  degradedSignals: [],
  ruleHits: [
    {
      ruleId: 'BALANCE_BREAK',
      points: 50,
      reason: 'Statement balance breaks at row 2 (2026-09-10)',
      evidence: { docId: 'STMT-1', rowIndex: 2, date: '2026-09-10', expectedBalance: 43550.0, statedBalance: 48550.0 },
    },
  ],
};

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

describe('CaseDetailPanel', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockImplementation((url: string) => (url.endsWith('/audit') ? json([]) : json(detail)));
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('shows only masked identifiers and readable BALANCE_BREAK evidence', async () => {
    render(<CaseDetailPanel caseId="c-1" user="analyst" />);

    expect(await screen.findByText('******234F')).toBeInTheDocument();
    expect(screen.getByText('XXXXXXXX4821')).toBeInTheDocument();
    expect(screen.queryByText(/^[A-Z]{5}[0-9]{4}[A-Z]$/)).toBeNull();
    expect(screen.queryByText(/^\d{9,}$/)).toBeNull();

    expect(screen.getByText('BALANCE_BREAK')).toBeInTheDocument();
    expect(screen.getByText(/43,550\.00/)).toBeInTheDocument();
    expect(screen.getByText(/48,550\.00/)).toBeInTheDocument();
    expect(screen.getByText(/2026-09-10/, { selector: 'p:not(.reason)' })).toBeInTheDocument();
  });

  it('shows the API error message when a disposition is rejected', async () => {
    render(<CaseDetailPanel caseId="c-1" user="analyst" />);
    await screen.findByText('******234F');
    fetchMock.mockImplementationOnce(() => json({ error: 'VALIDATION_FAILED', message: 'comment must be at least 10 characters' }, 400));

    fireEvent.change(screen.getByLabelText('Comment'), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: 'CLEARED' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('comment must be at least 10 characters');
    const [url, init] = fetchMock.mock.calls.at(-1)!;
    expect(url).toBe('/api/cases/c-1/disposition');
    expect((init as RequestInit).headers).toMatchObject({ 'X-User': 'analyst' });
  });
});
