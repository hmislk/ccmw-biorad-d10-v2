# BioradD10MW - Project Documentation

Java middleware that polls a Bio-Rad D-10 Hemoglobin Testing System over its
built-in web interface and forwards each sample's results to a hospital LIS
over a JSON REST API. See `README.md` for a short overview; this file is the
fuller reference.

## 1. What it does

Every `queryFrequencyInMinutes` minutes (and once immediately on startup),
the middleware:

1. Fetches the D-10's daily results HTML page (today's date, and yesterday's
   too if `queryForYesterdayResults` is true).
2. Reads the list of sample IDs on that page (patient name, if present, is
   stripped off - see section 5).
3. Skips any sample ID already recorded in the sent-samples registry
   (section 6).
4. For every new sample, downloads that sample's "Patient report" PDF from
   the analyzer and extracts from it:
   - the chromatogram chart image
   - the peak table
   - the report header / run metadata
5. Sends all of the above to the hospital LIS as individual JSON
   observations (section 3), and writes each accepted observation to the
   daily results-sent audit log (section 7).
6. Once the sample's primary A1c result has been accepted by the LIS, marks
   that sample ID as sent so it is never sent again.

## 2. Architecture / package layout

```
com.carecode.biorad.d10
|-- D10Middleware            entry point: loads config, wires everything, runs the poll timer
|-- config/
|   `-- AppConfig            loads and holds config.json
|-- analyzer/
|   |-- AnalyzerClient       talks to the D-10's DiaWeb.cgi web interface
|   |-- SampleIdParser       strips patient name off a raw Sample ID field
|   |-- PatientReportParser  turns a Patient report PDF into PatientReportData
|   |-- PeakTableTextParser  pure text parser: peak table rows, total area, concentration
|   `-- ReportHeaderTextParser  pure text parser: injection date/#, rack #/position, method, S/N, version
|-- model/
|   |-- PatientReportData    everything extracted from one Patient report PDF
|   |-- PeakResult           one peak table row
|   `-- ReportHeader         one report's header/run metadata block
|-- processing/
|   `-- SampleProcessor      orchestrates one polling cycle: fetch -> parse -> send -> dedup
|-- lims/
|   |-- LimsClient           builds and POSTs observation JSON to the hospital LIS
|   `-- ResultsAuditLog      writes to the daily results-sent audit log
`-- tracking/
    `-- ProcessedSamplesStore   the global "already sent" registry
```

Design principle: every text-parsing class (`PeakTableTextParser`,
`ReportHeaderTextParser`, `SampleIdParser`) is pure - text/string in,
data out, no PDF or network dependency - so each is unit-tested directly
against text and IDs transcribed from real printed D-10 reports
(`src/test/java/...`).

## 3. Result / observation codes sent to the LIS

Every observation is one JSON POST to `{limsServerBaseUrl}/observation`
(HTTP Basic Auth), built by `LimsClient.buildObservationJson(...)`. Per
sample, the following are sent:

### 3.1 Primary A1c result

| Coding system | Code | Value | Unit |
|---|---|---|---|
| `http://loinc.org` | `4548-4` | HbA1c % (from the report's Concentration box, falling back to the A1c peak's Area%) | `%` (`http://unitsofmeasure.org`) |

### 3.2 Full peak table - coding system `D10-PEAK` (config: `peakObservationCodingSystem`)

One row per peak actually present on the report, from this vocabulary (`PeakTableTextParser.KNOWN_PEAK_NAMES`):
`LA1c/CHb-1`, `A1a`, `A1b`, `A1c`, `A0`, `P1`, `P2`, `P3`, `P4`, `P5`, `F`,
`E`, `D`, `S`, `C`, `Variant`, `Unknown`.

For each peak, up to 4 component observations - `<Peak>^TIME` (`min`),
`<Peak>^HEIGHT` (`counts`), `<Peak>^AREA` (`counts`), `<Peak>^AREAPCT` (`%`).
Only the peaks that actually appear on a given sample's printout are sent;
the table below is the full possible vocabulary (17 peaks x 4 components =
68 codes), plus one totals code.

| Peak | TIME | HEIGHT | AREA | AREAPCT |
|---|---|---|---|---|
| LA1c/CHb-1 | `LA1c/CHb-1^TIME` | `LA1c/CHb-1^HEIGHT` | `LA1c/CHb-1^AREA` | `LA1c/CHb-1^AREAPCT` |
| A1a | `A1a^TIME` | `A1a^HEIGHT` | `A1a^AREA` | `A1a^AREAPCT` |
| A1b | `A1b^TIME` | `A1b^HEIGHT` | `A1b^AREA` | `A1b^AREAPCT` |
| A1c | `A1c^TIME` | `A1c^HEIGHT` | `A1c^AREA` | `A1c^AREAPCT` |
| A0 | `A0^TIME` | `A0^HEIGHT` | `A0^AREA` | `A0^AREAPCT` |
| P1 | `P1^TIME` | `P1^HEIGHT` | `P1^AREA` | `P1^AREAPCT` |
| P2 | `P2^TIME` | `P2^HEIGHT` | `P2^AREA` | `P2^AREAPCT` |
| P3 | `P3^TIME` | `P3^HEIGHT` | `P3^AREA` | `P3^AREAPCT` |
| P4 | `P4^TIME` | `P4^HEIGHT` | `P4^AREA` | `P4^AREAPCT` |
| P5 | `P5^TIME` | `P5^HEIGHT` | `P5^AREA` | `P5^AREAPCT` |
| F | `F^TIME` | `F^HEIGHT` | `F^AREA` | `F^AREAPCT` |
| E | `E^TIME` | `E^HEIGHT` | `E^AREA` | `E^AREAPCT` |
| D | `D^TIME` | `D^HEIGHT` | `D^AREA` | `D^AREAPCT` |
| S | `S^TIME` | `S^HEIGHT` | `S^AREA` | `S^AREAPCT` |
| C | `C^TIME` | `C^HEIGHT` | `C^AREA` | `C^AREAPCT` |
| Variant | `Variant^TIME` | `Variant^HEIGHT` | `Variant^AREA` | `Variant^AREAPCT` |
| Unknown | `Unknown^TIME` | `Unknown^HEIGHT` | `Unknown^AREA` | `Unknown^AREAPCT` |

Plus one totals observation: `TOTAL^AREA` (`counts`).

Set `"sendPeakTable": false` to skip this entire section.

### 3.3 Report header / run metadata - coding system `D10-META` (config: `headerObservationCodingSystem`)

| Code | Full code | Source field |
|---|---|---|
| `INJECTION_DATE` | `D10-META^INJECTION_DATE` | Injection date |
| `INJECTION_NUMBER` | `D10-META^INJECTION_NUMBER` | Injection # |
| `RACK_NUMBER` | `D10-META^RACK_NUMBER` | Rack # |
| `RACK_POSITION` | `D10-META^RACK_POSITION` | Rack position |
| `METHOD` | `D10-META^METHOD` | Method |
| `SERIAL_NUMBER` | `D10-META^SERIAL_NUMBER` | Instrument S/N |
| `SOFTWARE_VERSION` | `D10-META^SOFTWARE_VERSION` | Bio-Rad software version |

Any field not found in the report text is simply omitted (not sent as
blank). Set `"sendReportHeader": false` to skip this entire section.

### 3.4 Chromatogram chart - coding system `D10-IMG` (config: `chromatogramObservationCodeSystem`)

| Code | Value |
|---|---|
| `D10-CHROMATOGRAM` (config: `chromatogramObservationCode`) | `^Image^PNG^Base64^<base64 PNG bytes>` |

The peak table, header metadata and chromatogram all come from the *same*
per-sample PDF (`?page=pdf&test=HBA1C&nbfile=1&f0=...`) - one download, two
extractions (image + text). If a given analyzer/software version renders
that PDF as a single flattened image with no text layer, peak-table and
header extraction silently find nothing (logged as a warning); the
chromatogram image and primary A1c result are unaffected. Fixing that would
require OCR, which is not implemented here.

## 4. Every field in the LIMS JSON payload

Built by `LimsClient.buildObservationJson`, sent for *every* observation
above:

| JSON field | Source |
|---|---|
| `sampleId` | parsed sample ID (section 5) |
| `observationValue` | the value described in section 3 |
| `observationValueCodingSystem` / `observationValueCode` | as per section 3 |
| `observationUnitCodingSystem` / `observationUnitCode` | as per section 3 (empty for header fields and the chromatogram) |
| `analyzerId`, `departmentAnalyzerId`, `analyzerName`, `departmentId` | from config |
| `username`, `password` | LIMS credentials, from config |
| `issuedDate` | timestamp of the poll cycle that sent it |

## 5. Sample ID vs. patient name

MLTs enter the Sample ID at the analyzer, sometimes with the patient's name
appended using whatever separator is at hand: underscore, hyphen, or space
(e.g. `1258968_Damith`, `1458962-Damith`, `125486 Damith`, or as seen on
real printed reports, `10170973-DAYANI`). Sometimes only the bare sample ID
is entered, with no name at all (e.g. `591318`) - both forms are supported.

`SampleIdParser.extractSampleId(...)` returns just the text before the
first separator, and is applied everywhere a sample ID is read from the
analyzer (`AnalyzerClient.extractSampleIds`, `extractCheckboxKeys`), so the
patient name is never sent to the LIS as part of the specimen identifier,
never used as a dedup key, and never appears in any log.

## 6. Duplicate-send prevention

`ProcessedSamplesStore` keeps a single flat, ever-growing registry file,
`state/sent_samples.txt` (one sample ID per line; a lab easily runs a few
thousand samples a year, so this file stays tiny indefinitely). Once a
sample's primary A1c result has been accepted by the LIS, its ID is
appended to this file and it is never sent again - this is deliberately
*not* scoped per day, so a sample that reappears in a later poll (e.g.
because it also falls in the "yesterday" query window near a day boundary)
is still skipped. Only a sample whose primary A1c result failed to send is
retried on the next poll.

## 7. Logs

Configured in `src/main/resources/logback.xml`, one concern per file
(override the directory with `-DLOG_DIR=/path/to/logs`):

| File | Content | Retention |
|---|---|---|
| `logs/app.log` | general application / analyzer-polling activity | 90 days |
| `logs/lims.log` | every request/response exchanged with the LIMS | 90 days |
| `logs/error.log` | every ERROR-level entry, from any component | 180 days |
| `logs/results-sent/results-sent-YYYY-MM-DD.log` | one line per observation actually accepted by the LIS that day - a new file every day | kept indefinitely |

## 8. Configuration reference (`config.json`)

Copy `config.json.example` to `config.json` and fill in real values. Keys
live under `middlewareSettings`:

| Key | Location | Meaning | Default |
|---|---|---|---|
| `analyzerBaseURL` | `analyzerDetails` | base URL of the D-10's DiaWeb.cgi interface | - (required) |
| `serialNumber` | `analyzerDetails` | instrument serial number (informational) | - |
| `analyzerId` | `analyzerDetails` | sent in every LIMS payload | - (required) |
| `departmentAnalyzerId` | `analyzerDetails` | sent in every LIMS payload | - (required) |
| `departmentId` | `analyzerDetails` | sent in every LIMS payload | - (required) |
| `analyzerName` | `analyzerDetails` | sent in every LIMS payload | - (required) |
| `limsServerBaseUrl` | `limsSettings` | LIS REST base URL (`/observation` is appended) | - (required) |
| `username` / `password` | `limsSettings` | LIS Basic Auth credentials | - (required) |
| `queryFrequencyInMinutes` | `communication` | poll interval | - (required) |
| `queryForYesterdayResults` | `communication` | also poll yesterday's date range each cycle | - (required) |
| `chromatogramObservationCodeSystem` | top-level | coding system for the chromatogram | `D10-IMG` |
| `chromatogramObservationCode` | top-level | code for the chromatogram | `D10-CHROMATOGRAM` |
| `peakObservationCodingSystem` | top-level | coding system for peak-table observations | `D10-PEAK` |
| `sendPeakTable` | top-level | send the peak table at all | `true` |
| `headerObservationCodingSystem` | top-level | coding system for report-header observations | `D10-META` |
| `sendReportHeader` | top-level | send the report header at all | `true` |
| `processedSamplesDirectory` | top-level | directory for `sent_samples.txt` | `state` |
| `disableSslVerification` | top-level | trust all certs when talking to the analyzer (dev only) | `false` |

`config.json` is gitignored - real credentials never enter the repo.

## 9. Build, test, run

```
mvn package        # compiles, runs tests, produces target/BioradD10MW-1.0.jar (shaded)
mvn test           # tests only
java -jar target/BioradD10MW-1.0.jar config.json    # config path defaults to ./config.json
```

Java 11, Maven. Key dependencies: jsoup (HTML scraping), org.json (JSON
payloads), pdfbox (PDF image/text extraction), slf4j + logback (logging),
junit-jupiter (tests), maven-shade-plugin (fat jar).

## 10. Tests

| Test class | Covers |
|---|---|
| `PeakTableTextParserTest` | peak table rows, the `LA1c/CHb-1` vs `A1c` disambiguation, total area, concentration |
| `ReportHeaderTextParserTest` | all header fields, partial/missing fields, blank text |
| `SampleIdParserTest` | underscore/hyphen/space-separated names, real printed IDs, bare IDs with no name, whitespace trimming |

## 11. Known limitations

- Peak-table and header extraction depend on the Patient report PDF having
  a real text layer. If an analyzer/software version ever renders it as a
  flattened image, those two sections silently produce nothing (logged as a
  warning); OCR would be needed to support that case, and is not
  implemented.
- Peak-table and header regexes were validated against text transcribed
  from real printed reports, not yet against a PDF generated directly by
  the analyzer - worth confirming against a live-generated PDF once
  available.
