# DATOP aggregate import

## Source and scope

Official dataset: [DSET000004, interbank transfer history](https://www.datop.or.kr/data/set/dataSetDetail.do?dataSetId=DSET000004).

The actual schema contains BASE_YM, HNDE_BANK_RPTV_CODE, OPENBANK_RPTV_CODE, FND_TPCD and TRAN_AMT (NUMBER(23)). These are monthly aggregates with anonymized bank codes, not individual account transactions. Import uses separate `datop_aggregate_datasets` and `datop_transfer_aggregates` tables; it does not create ledger entries or modify balances.

On 2026-11-21, only the five rows visible in the official public preview were fetched. The full file download requires DATOP login and has **not** been completed. The preview is labeled `PUBLIC_PREVIEW`; synthetic mock transactions are unrelated.

## Local preview

```powershell
python scripts/fetch_datop_preview.py
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-17' :app:datopBootRun '-PdatopFile=datop-data/preview/DSET000004-public-preview.csv' '-PdatopSourceKind=PUBLIC_PREVIEW'
```

Open http://localhost:8083/datop-demo. This task uses a local in-memory H2 database, which is cleared on process exit. Archived source files remain on disk. It uses the mock profile and does not call KFTC APIs.

## Downloaded original

After logging in to DATOP, download the dataset CSV into `datop-data/raw/`. Stop the existing datopBootRun before running:

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-17' :app:datopBootRun '-PdatopFile=datop-data/raw/actual-file.csv' '-PdatopSourceKind=DOWNLOADED_FILE'
```

Use the actual filename. If necessary, pass `-PdatopEncoding=MS949` explicitly. Only the documented five English or Korean headers are accepted; a different downloaded schema must be inspected before changing the importer. `DOWNLOADED_FILE` describes the supplied file, not independent proof of its provenance or completeness.

Source bytes are archived under `datop-data/archive/<SHA-256>`. Dataset metadata records source URL, source kind, hash, row count and import time. The same owner/kind/hash is imported once. An invalid row rolls back the whole database import. Local source data and archives are ignored by Git.

## Query and verification

`GET /api/datop/datasets` and `GET /api/datop/datasets/{id}/aggregates?fromMonth=202107&toMonth=202107&limit=2` use the project's `X-User-Id` convention. Demo owner is `mock-demo-user`; this header is a local demo convention, not production authentication. Pagination uses `afterId`; `handlingBank` optionally filters the anonymized bank code. Amounts are returned as strings to preserve all 23 digits.

Verified locally on 2026-11-21:

- Five official preview rows matched the imported API response exactly, across three pages of size two.
- CSV SHA-256: `f869d54560eddc58c95228e06ee6add4f1734bcf0c8d98fc983435196b1d705d`.
- Aggregate amount sum: `83686443712000`; bank filter and foreign-owner HTTP 403 passed.
- Java tests: 36 total, 29 passed, 7 skipped, zero failures/errors. New tests cover exact source archiving, duplicate import, 23-digit precision, pagination, index creation, ownership, invalid month and rollback after a flushed batch.
- Public HTML parser validated the actual five preview headers and rows. Demo page HTTP 200 confirmed; visual browser inspection was unavailable because browser control failed.

This is a functional import verification, not a large-volume performance benchmark. No index/cache performance improvement is claimed from five rows. Full downloaded-file import remains pending acquisition of that file.
