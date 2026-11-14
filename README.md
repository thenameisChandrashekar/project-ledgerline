# Project Ledgerline

A service for a lender's Fraud Control Unit (FCU). It receives the already-extracted contents of a loan file
(applicant, declared income, salary slips, bank statement), runs explainable consistency checks, opens a case only
when the evidence justifies it, and gives analysts a screen to work those cases with a full audit trail.

All data is synthetic and produced by the bundled generator.

## What is built

| Task | Status |
|---|---|
| 1. Loan-file consistency API: 5 rules, risk band, evidence, PostgreSQL, cases, masking, generator, evaluation table | Done |
| 2. `DOCUMENT_REUSED` across files with Redis, idempotent submissions, degraded mode with REFER floor, load runner | Done |
| 3. Audited dispositions (`case_audit`), React + TypeScript case queue and detail panel | Done (merged via PR from `feat/fcu-case-screen`) |
| Stretch | CI workflow (GitHub Actions: `mvn test` + `npm test`) |

## Architecture

```
            ┌────────────────────── backend (Spring Boot 3, Java 17) ───────────────────────┐
 POST       │ api/            service/                 engine/ (no Spring)                  │
 /api/loan- │ LoanFile   ──▶  LoanFileService  ──────▶ RuleEngine ─▶ 5 Rule classes        │
 files ────▶│ Controller      │  idempotency (fileId +  RiskBander (thresholds from yml)     │
            │                 │  payload hash)                                              │
            │                 │                         fingerprint/ (no Spring)            │
            │                 ├─ 50 ms timeout ───────▶ DocumentReuseChecker               │
            │                 │  (degraded → REFER)      └─ DocumentFingerprintStore ──────┼──▶ Redis (ZSETs + TTL)
            │                 │                             InMemory (tests) / Redis impl   │
            │                 └─ one transaction ─────▶ persistence/ (JPA) ────────────────┼──▶ PostgreSQL (Flyway)
 GET/POST   │ CaseController ─▶ CaseQueryService / DispositionService (state machine,     │     loan_file, rule_hit,
 /api/cases │                   append-only audit)    masking only at the API/log edge   │     fcu_case, case_audit
            └────────────────────────────────────────────────────────────────────────────────┘
 frontend (React + TS, Vite) ── /api proxy ──▶ backend
```

Key packages (`backend/src/main/java/com/azentio/ledgerline`):

- `domain` – request records with Bean Validation (PAN regex, `YYYY-MM`, `CR|DR`, 1–6 slips, non-empty transactions).
- `engine` – pure Java rules, `RulesConfig`, `RiskBander`, `NameNormalizer`. Unit-tested without Spring.
- `fingerprint` – canonical statement hashing, `DocumentFingerprintStore` (Redis + in-memory), `DocumentReuseChecker` with an injectable `Clock`.
- `service` – orchestration, idempotency, degraded mode, dispositions; `ResultMapper` is the single place identifiers are masked.
- `tools` – data generator CLI, offline evaluator, load runner.

## Running it

Prerequisites: Java 17+, Maven 3.9+, Node 20+, Docker.

```bash
cp .env.example .env                 # optional, defaults work for local use
docker compose up -d                 # PostgreSQL 16 + Redis 7
cd backend && mvn spring-boot:run    # http://localhost:8080 (Flyway migrates on start)
```

Post a sample file:

```bash
bash scripts/generate.sh 330 data/generated   # 330 files incl. 30 recycled statements + labels.csv
curl -s -X POST localhost:8080/api/loan-files -H 'Content-Type: application/json' \
  --data @data/generated/files/LF-00001.json | jq
bash scripts/post-files.sh data/generated/files # post all, prints counts per band
curl -s 'localhost:8080/api/cases?status=OPEN' | jq '.[0]'
```

UI (with the backend running):

```bash
cd frontend && npm install && npm run dev     # http://localhost:5173, proxies /api to :8080
```

### FCU case screen

- **Queue (left):** table of cases with score, band badge (LOW/REFER/HIGH), applicant, file id and status, highest
  score first (sorted by the API). A status dropdown filters OPEN, REFER_FIELD_VERIFICATION, CLEARED, FRAUD_CONFIRMED or
  ALL. Clicking a row opens the case.
- **Detail (right):** applicant, file id, masked PAN (`******234F`) and account (`XXXXXXXX4821`), score, band and
  status; a warning if a signal was degraded; one card per rule hit with points, reason and readable evidence (for
  example "Row 41 on 2026-07-18: expected balance ₹88,210.40, statement shows ₹131,210.40", the unmatched salary months,
  the edited documents and failed checks, or the other files sharing a statement); the audit history (time, analyst,
  from → to, comment).
- **Dispositions:** a comment box and only the buttons the state machine allows (OPEN: Cleared / Refer field
  verification / Fraud confirmed; REFER_FIELD_VERIFICATION: Cleared / Fraud confirmed; none for final states). The
  analyst name in the header is sent as `X-User`. If the API rejects the call (short comment, invalid transition) its
  error message is shown under the buttons; on success the case and queue refresh.

## Design decisions

- **Pure rule engine.** `engine` and `fingerprint` have no Spring imports, so every rule is unit-tested directly and
  the module could be reused by a CLI or another service. Spring only wires configuration and I/O.
- **Configuration over code.** Every point value, tolerance, list and threshold binds from `application.yml` into
  `RulesConfig`; tests prove a config change moves a file between bands.
- **Explainability first.** Each rule returns a reason string and a structured evidence map that is stored as JSON
  and rendered by the UI, so an analyst never sees a bare score.
- **Masking at the boundary.** Full identifiers stay in PostgreSQL; `ResultMapper` and log statements are the only
  exits and always mask. Redis gets a PAN digest only.
- **Fail safe, not open.** When Redis is slow or down the file is still evaluated, flagged as degraded and floored to
  REFER, and its fingerprints are not recorded.
- **Database-enforced idempotency.** A unique constraint on `file_id` plus a payload hash gives replay (200) vs
  conflict (409) even under concurrent duplicates.
- **Server-side state machine.** Allowed disposition transitions live in `DispositionService`; the UI only mirrors
  them, and every change appends an audit row in the same transaction.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/loan-files` | 201 new result · 200 replay of an already-processed `fileId` with the same payload · 409 same `fileId`, different payload · 400 with `fieldErrors` |
| GET | `/api/cases?status=OPEN` | Highest score first. `status` optional (all statuses when omitted) |
| GET | `/api/cases/{caseId}` | Case with masked PAN/account, rule hits with reasons and evidence, `degradedSignals` |
| POST | `/api/cases/{caseId}/disposition` | Body `{disposition, comment}`, header `X-User`. 409 invalid transition, 400 comment < 10 chars |
| GET | `/api/cases/{caseId}/audit` | Append-only disposition history |

Example response (illustrative values, abridged):

```json
{
  "fileId": "LF-00017", "applicantName": "Meera Iyer",
  "panMasked": "******412K", "accountNumberMasked": "XXXXXXXX0193",
  "score": 50, "band": "REFER", "degradedSignals": [], "caseId": "7f0c…",
  "ruleHits": [{
    "ruleId": "BALANCE_BREAK", "points": 50,
    "reason": "Statement balance breaks at row 41 (2026-07-18): expected 88210.40 but statement shows 131210.40",
    "evidence": {"docId": "LF-00017-STMT", "rowIndex": 41, "date": "2026-07-18",
                 "expectedBalance": 88210.4, "statedBalance": 131210.4}
  }]
}
```

## Rules and configuration

All points, tolerances, the editing-tool list, the reuse window and band thresholds live in
`backend/src/main/resources/application.yml` under `ledgerline.rules` (bound to `RulesConfig`). Nothing is hard-coded
in rule logic.

| Rule | Points | Fires when |
|---|---|---|
| `BALANCE_BREAK` | 50 | A transaction's stated balance differs from previous ± amount by more than 0.01. Evidence: first breaking row index, date, expected and stated balance |
| `SALARY_CREDIT_MISSING` | 40 | A slip has no CR in its month or the first 7 days of the next whose normalised narration contains the normalised employer and whose amount is within 2% of net pay. Evidence: unmatched months |
| `DECLARED_INCOME_ABOVE_SLIPS` | 25 | Declared income > 1.2 × average slip net pay |
| `DOC_METADATA_SUSPECT` | 30 | Any document produced by a listed editing tool (case-insensitive), with incremental updates, or modified > 24 h after creation. Fires once per file; evidence lists each docId and failed checks |
| `NAME_MISMATCH` | 20 | Statement holder or a slip employee name differs from the applicant after normalising case, whitespace, dots and token order |
| `DOCUMENT_REUSED` | 60 | Statement `templateHash` seen on another file with a different PAN in the last 180 days |

Bands: `< 30` LOW, `30–59` REFER, `≥ 60` HIGH. REFER and HIGH open a case.

Employer normalisation: upper-case, punctuation to spaces, collapse spaces, drop the tokens `PVT PRIVATE LTD LIMITED`,
then match as a whole-token sequence inside the normalised narration (so `Northwind Analytics Pvt. Ltd.` matches
`NEFT/NORTHWIND ANALYTICS PRIVATE LIMITED/SAL AUG`, but `NORTHWINDS` does not).

Config changes the outcome without code changes – `ConfigurableThresholdTest` boots the app with
`ledgerline.rules.name-mismatch.points=35` and shows the same file moving from LOW to REFER; `RulesTest` does the same
for the band threshold.

## Recycled-document detection (Task 2)

**Canonical form** (`StatementFingerprinter`), UTF-8, one line per field, `\n` separated:

```
holderName=<trimmed, single-spaced, upper-case>      ← content form only
accountNumber=<trimmed>                              ← content form only
openingBalance=<plain decimal, scale 2, HALF_UP>
txn=<yyyy-MM-dd>|<CR|DR>|<amount scale 2>|<balance scale 2>|<narration trimmed, single-spaced, upper-case>   (per row, in order)
```

`docId` and `meta` are never included. `contentHash = SHA-256(content form)`, `templateHash = SHA-256(template form)`,
hex encoded. Amounts are rescaled so `10000` and `10000.00` hash the same.

**Store.** Two Redis sorted sets per statement, `ledgerline:fp:template:<hash>` and `ledgerline:fp:content:<hash>`.
Member `fileId|sha256(PAN)` (no clear PAN in Redis), score = `submittedAt` epoch ms. Every read and write runs
`ZREMRANGEBYSCORE` for entries older than the window and every write sets `EXPIRE` to window + 1 day, so keys cannot
grow without bound. `DocumentReuseChecker` excludes its own `fileId` and the same PAN digest; evidence reports
`contentHash` when the statement is a byte-for-byte copy, otherwise `templateHash`, plus up to 5 other fileIds.

**Idempotency.** `loan_file.file_id` has a unique constraint. A repeated `fileId` with the same payload hash returns
the stored result (200) without new rows, a new case or new fingerprints; a different payload returns 409. A concurrent
duplicate that loses the race on the constraint is answered from the stored row.

**Degraded mode.** Fingerprint calls run on a bounded pool with a 50 ms timeout (`ledgerline.fingerprint.timeout-ms`).
On timeout or error the other rules still run, the response has `"degradedSignals": ["DOCUMENT_REUSED"]`, a warning is
logged with masked identifiers, and a file that would otherwise be LOW is banded REFER and opens a case. Fingerprints
for a degraded file are not recorded.

## Tests

```bash
cd backend && mvn test                 # offline: H2 (PostgreSQL mode) + in-memory fingerprint store, no Docker
cd backend && mvn test -Pintegration   # Docker required: Testcontainers Redis store test
cd frontend && npm test                # Vitest + React Testing Library
```

What is covered:

- every rule positive and negative, LOW/REFER at exactly 30 and REFER/HIGH at exactly 60 (`RulesTest`)
- salary credited on the 3rd of next month, 1% off, different narration → no `SALARY_CREDIT_MISSING`
- single edited balance mid-statement → `BALANCE_BREAK` with that row index
- `GET /api/cases` and `GET /api/cases/{id}` contain no full PAN or account number (`LoanFileApiTest`)
- fixed-clock reuse window: fires at 100 days, not at 181, not for the same PAN, never against itself (`DocumentReuseCheckerTest`)
- template vs content hash behaviour (`StatementFingerprinterTest`)
- same fileId twice → one loan file, one set of hits, one case; changed payload → 409; store throws or is slow → completed response with `degradedSignals` and REFER floor (`IdempotencyAndDegradedModeTest`)
- valid disposition writes an audit row; acting on CLEARED → 409; short comment → 400 (`DispositionApiTest`)
- UI: detail panel shows only masked identifiers and BALANCE_BREAK expected vs stated balance; API error is shown on a rejected disposition

## Measured numbers

### Rule evaluation on the generated dataset

`bash scripts/evaluate.sh` (or `DatasetEvaluationTest`, which asserts the targets) – 330 files, seed 42, evaluated in order
with the in-memory fingerprint store:

| Seeded pattern | Files | Expected rule | Fired expected rule | Fired any rule | REFER/HIGH |
|---|---|---|---|---|---|
| CLEAN | 180 | - | - | 0 | 0 |
| TAMPERED_BALANCE | 24 | BALANCE_BREAK | 24/24 | 24 | 24 |
| FABRICATED_SLIP | 24 | SALARY_CREDIT_MISSING | 24/24 | 24 | 24 |
| INFLATED_DECLARATION | 24 | DECLARED_INCOME_ABOVE_SLIPS | 24/24 | 24 | 0 |
| EDITED_METADATA | 24 | DOC_METADATA_SUSPECT | 24/24 | 24 | 24 |
| NAME_SWAP | 24 | NAME_MISMATCH | 24/24 | 24 | 0 |
| RECYCLED_DOCUMENT | 30 | DOCUMENT_REUSED | 30/30 | 30 | 30 |

Every seeded fraud file fires its expected rule; 0% of CLEAN files land in REFER/HIGH (target ≤ 2%).
INFLATED_DECLARATION (25) and NAME_SWAP (20) alone stay LOW by design of the point table.

Clean files carry realistic noise: salary credited on the 1st–5th of the next month, five bank narration formats with
different suffix spellings, credits up to 1% off slip net pay, 8–21 everyday transactions per month including small
refund and interest credits, and names that differ only in case and spacing.

### Load test

```bash
docker compose down -v && docker compose up -d   # clean PostgreSQL + Redis
cd backend && mvn -q -DskipTests package && java -jar target/ledgerline-0.1.0.jar &
bash scripts/load.sh http://localhost:8080 1000 10 50  # 1000 new files, 10 clients, 50 recycled
```

The runner prints counts per band, `DOCUMENT_REUSED` hits, recycled files flagged, and p50/p95 latency.

<!-- LOAD-RESULTS: paste the output of scripts/load.sh here -->
_Not yet measured: the run needs PostgreSQL and Redis, which were not available while this was written. Target is
p95 < 150 ms on a laptop; the command above prints band counts, `DOCUMENT_REUSED` hits, recycled files flagged and
p50/p95._

## Security and data handling

- PAN and account number are masked (`******234F`, `XXXXXXXX4821`) in every API response and every log line; rule
  evidence never contains them. Full values are stored only in PostgreSQL.
- Redis holds only a SHA-256 digest of the PAN.
- No secrets in the repo: configuration via environment variables, `.env.example` committed, `.env` ignored.
- Input validated with Bean Validation; malformed JSON and wrong types return 400 with the offending field.
- `case_audit` is append-only: the repository exposes only `save` and reads, the entity is `@Immutable`.

## What I would do next

- Make the audit table append-only at the database level too (PostgreSQL trigger or revoke UPDATE/DELETE from the app role).
- HMAC the PAN digest with a secret so Redis contents cannot be brute-forced over the PAN space.
- Testcontainers end-to-end test for the full API on PostgreSQL + Redis; currently the integration profile covers the Redis store.
- Pagination and filters (band, date) on the case queue; authentication instead of a free-text `X-User` header.
- Extract `engine` + `fingerprint` into a Spring-free Maven module with a CLI (they already have no Spring imports).
- Publish case-opened / case-disposed events (outbox → Kafka) for loan origination and reporting.
