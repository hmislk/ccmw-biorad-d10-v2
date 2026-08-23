# BioradD10MW

Java middleware that polls a Bio-Rad D-10 Hemoglobin Testing System over its
web interface, downloads each sample's "Patient report" PDF, and sends the
A1c result, the full peak table, and the chromatogram chart to a hospital
LIMS over a JSON REST API.

This is a clean rewrite of `ccmw-biorad-d10`, built against the D-10 LIS
Interface Requirements manual (Bio-Rad L20017700 / L20017703) for the field
and peak naming conventions, and reusing that project's proven analyzer/LIMS
integration approach (HTML result-list scraping + PDF report download, JSON
POST to `{limsServerBaseUrl}/observation`).

## What gets sent to the LIS, per sample

1. **Primary A1c result** — LOINC `4548-4`, in `%` (UCUM `http://unitsofmeasure.org`).
2. **Full peak table** — one observation per peak (A1a, A1b, F, LA1c/CHb-1,
   A1c, P3, A0) per field: `<Peak>^TIME`, `<Peak>^HEIGHT`, `<Peak>^AREA`,
   `<Peak>^AREAPCT`, coded under `peakObservationCodingSystem` (default
   `D10-PEAK`). Also `TOTAL^AREA`. Set `"sendPeakTable": false` in config to
   skip this and only send the primary result + chromatogram.
3. **Report header / run metadata** — Injection date, Injection #, Rack #,
   Rack position, Method, instrument S/N and Bio-Rad software version, one
   observation per field, coded under `headerObservationCodingSystem`
   (default `D10-META`), e.g. `D10-META^INJECTION_DATE`,
   `D10-META^RACK_POSITION`. Any field the report text doesn't contain is
   simply omitted rather than blocking the rest. Set `"sendReportHeader":
   false` in config to skip this.
4. **Chromatogram chart** — the chart image embedded in the Patient report
   PDF, base64-encoded PNG, sent as one observation coded under
   `chromatogramObservationCodeSystem` / `chromatogramObservationCode`.

The peak table, header metadata and chromatogram all come from the *same*
per-sample PDF report the analyzer generates
(`?page=pdf&test=HBA1C&nbfile=1&f0=...`) — one download, two extractions
(image + text). If a given analyzer/software version turns out to render
that PDF as a single flattened image with no text layer, peak-table and
header extraction will silently find nothing (logged as a warning) while the
chromatogram image and primary A1c result are unaffected; that would need
OCR to fix, which is not implemented here.

## Duplicate sends

Once a sample's results have been accepted by the LIS, that sample is never
sent again — this is tracked in `state/sent_samples.txt`, a flat,
ever-growing registry of every sample ID ever sent (see
`ProcessedSamplesStore`). This is deliberately *not* scoped per day: a
sample that shows up again in a later poll (e.g. because it also falls in
the "yesterday" query window near a day boundary) is still skipped. Only a
sample whose primary A1c result failed to send is retried on the next poll.

## Sample ID vs. patient name

MLTs enter the Sample ID at the analyzer, and sometimes add the patient's
name alongside it using whatever separator is at hand — underscore, hyphen,
or space (e.g. `1258968_Damith`, `1458962-Damith`, `125486 Damith`), or, as
seen on real printed reports, `10170973-DAYANI`. Other times only the bare
sample ID is entered (e.g. `591318`), with no name at all — both forms are
supported. `SampleIdParser` extracts just the ID (the text before the first
separator) wherever a sample ID is read from the analyzer, so the patient
name is never sent to the LIS as part of the specimen identifier, never
used as a dedup key, and never appears in any log.

## Logs

Configured in `src/main/resources/logback.xml`, one concern per file:

- `logs/app.log` — general application / analyzer-polling activity.
- `logs/lims.log` — every request/response exchanged with the LIMS.
- `logs/error.log` — every ERROR-level entry, from any component, for fast triage.
- `logs/results-sent/results-sent-YYYY-MM-DD.log` — a **new file every day**,
  one line per observation actually accepted by the LIS that day. This is
  the audit trail of what was sent to the LIS, kept indefinitely.

Override the log directory with `-DLOG_DIR=/path/to/logs`.

## Configuration

Copy `config.json.example` to `config.json`, fill in the analyzer and LIMS
details, and run:

```
java -jar target/BioradD10MW-1.0.jar config.json
```

(the config path defaults to `config.json` in the working directory if omitted).

## Build

```
mvn package
```

Produces a shaded `target/BioradD10MW-1.0.jar` with all dependencies bundled.

## Tests

`PeakTableTextParserTest` checks the peak-table parser against text
transcribed from an actual printed Patient report, including the tricky
case of `LA1c/CHb-1` not being mistaken for a second `A1c` row.
