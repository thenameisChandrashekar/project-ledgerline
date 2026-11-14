# AI usage

## Tools

- **Claude (Cowork, Anthropic)** – primary coding agent. It read the assignment PDF, proposed the design, generated
  the backend, generator, tests, React UI and docs, and ran what it could in its sandbox.
- I reviewed every file, decided the design trade-offs listed below, and ran the full build and tests locally.

## How I drove it (key prompts)

1. "Read the assignment PDF and summarise the requirements and 'Done means' per task."
2. "Build Task 1: Spring Boot 3 / Java 17, rules in plain Java classes testable without Spring, every threshold in
   application.yml, PAN/account masked at the API and log boundary, Flyway schema with a status+score index."
3. "Write a deterministic generator for ≥300 files, ≥50% CLEAN with realistic noise (salary on the 1st–5th, varied
   narrations, ±1% credit, many non-salary transactions) and an evaluator that prints the per-pattern table."
4. "Task 2: canonical statement fingerprints, DocumentFingerprintStore with Redis sorted sets scored by submittedAt and
   key TTLs, injectable Clock, fileId idempotency enforced by a DB unique constraint, 50 ms timeout with REFER floor."
5. "Task 3 on a feature branch: disposition state machine, append-only audit, React + TS queue/detail with RTL tests."
6. "Review the Spring/JPA code for H2-vs-PostgreSQL and Hibernate pitfalls before I run it."

## Design decisions I made

- Rules return `Optional<RuleHit>` with structured evidence; the engine has no Spring imports, so it is unit-tested
  directly and can become a standalone module.
- Masking happens in one place (`ResultMapper`) plus log statements, so evidence and DB rows stay unmasked internally
  but nothing leaves the service unmasked.
- `DOCUMENT_REUSED` is evaluated in the service (it needs I/O and a timeout), not in the pure rule engine.
- Degraded files get no fingerprints recorded, and are floored to REFER rather than silently passing.
- PAN stored in Redis only as a SHA-256 digest.

## Where the AI was wrong and how it was caught

| Problem | How it was caught | Fix |
|---|---|---|
| First FABRICATED_SLIP generator removed the salary credit for the latest month, but the previous month's credit (paid 1st–5th) also falls inside the latest slip's month window and matched it. Only 14/24 fired. | The offline evaluator table | Changed the pattern to what fraud rings actually do: slips name an employer that never paid the applicant. Now 24/24. |
| A rule test expected a credit on 9 Sep to leave both Aug and Sep unmatched; it legitimately matches the Sep slip. | Running the rules in a scratch harness | Split into two tests with correct expectations. |
| `InMemoryFingerprintStore` called `removeIf` on `List.of()` for unknown keys, which throws `UnsupportedOperationException`. | Code review | Return early for missing keys. |
| Flyway DDL used `NOT NULL DEFAULT 0` and a column named `at`, both risky on H2 in PostgreSQL mode. | Review of H2 grammar; migrations also applied to a real PostgreSQL 16 | `DEFAULT 0 NOT NULL`, column `acted_at`. |
| `CaseEntity` had a primitive `@Version` with an assigned id, so Spring Data would call `merge` instead of `persist` for new cases. | Review | `Long version`. |
| Surefire would not pick up `RedisFingerprintStoreIT` under `-Pintegration` (default includes don't match `*IT`). | Review of surefire defaults | Renamed to `*IntegrationTest`. |
| The RTL masking assertion regex matched across adjacent elements' text ("XXXXX…4821" + "Score"). | Failing `npm test` | Assert per element instead of on concatenated text. |
| Two `SALARY_CREDIT_MISSING` tests removed or altered the *September* credit, but the August salary paid on 2 Sep also sits inside September's window (month + 7 days) and matched it, so the rule correctly did not fire. | Local `mvn test` | Tests now alter the August credit, with a comment explaining the overlap. |
| The idempotency test asserted exactly 2 rule hits, but another test in the same Spring context had already fingerprinted the same ledger under a different PAN, so `DOCUMENT_REUSED` legitimately fired too (test-order dependence). | Local `mvn test` | Assert the stored hit count equals the first response's, not a constant. |
| GitHub Actions `hashFiles` was used in a job-level `if`, which is not allowed. | Review | Moved to the step. |

## Limits of the AI environment

The agent's sandbox could not reach Maven Central, so it could not run `mvn test` itself. It compiled and exercised the
pure-Java engine and generator with `javac`, validated the Flyway SQL on PostgreSQL 16 and ran the frontend tests.
The Spring Boot build and backend tests were run on my machine.

## Note on the commit history

The project was developed locally as a sequence of small Conventional Commits with a feature branch for Task 3.
`git push` from my machine was rejected with HTTP 403, so the commits were re-created on GitHub through the web
uploader (directory by directory, in the original order, with the original messages). That is why some commits are
split per folder and a few say "add missing" files: the web uploader occasionally dropped files from a batch, and
they were re-uploaded afterwards.
