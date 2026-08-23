package com.carecode.biorad.d10.processing;

import com.carecode.biorad.d10.analyzer.AnalyzerClient;
import com.carecode.biorad.d10.analyzer.PatientReportParser;
import com.carecode.biorad.d10.config.AppConfig;
import com.carecode.biorad.d10.lims.LimsClient;
import com.carecode.biorad.d10.lims.ResultsAuditLog;
import com.carecode.biorad.d10.model.PatientReportData;
import com.carecode.biorad.d10.model.PeakResult;
import com.carecode.biorad.d10.model.ReportHeader;
import com.carecode.biorad.d10.tracking.ProcessedSamplesStore;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives one polling cycle: fetch the day's sample list from the analyzer,
 * download and parse each new sample's Patient report PDF, and send the A1c
 * result, the full peak table, and the chromatogram chart to the LIS.
 *
 * A sample is checked against (and, once sent, recorded in) the global
 * sent-samples registry, not a per-date one -- once a sample's results have
 * gone to the LIS they are never sent again, no matter how many more times
 * that sample's date range gets polled.
 */
public class SampleProcessor {

    private static final Logger log = LoggerFactory.getLogger(SampleProcessor.class);

    private final AppConfig config;
    private final AnalyzerClient analyzerClient;
    private final PatientReportParser reportParser;
    private final LimsClient limsClient;
    private final ResultsAuditLog auditLog;
    private final ProcessedSamplesStore processedSamplesStore;

    public SampleProcessor(AppConfig config, AnalyzerClient analyzerClient, PatientReportParser reportParser,
                            LimsClient limsClient, ResultsAuditLog auditLog,
                            ProcessedSamplesStore processedSamplesStore) {
        this.config = config;
        this.analyzerClient = analyzerClient;
        this.reportParser = reportParser;
        this.limsClient = limsClient;
        this.auditLog = auditLog;
        this.processedSamplesStore = processedSamplesStore;
    }

    public void processDate(LocalDate date) {
        String url = analyzerClient.generateUrlForDate(date);
        String html = analyzerClient.fetchHtmlContent(url);
        if (html == null) {
            return;
        }

        List<String> sampleIds = analyzerClient.extractSampleIds(html);
        if (sampleIds.isEmpty()) {
            log.info("No samples found for {}", date);
            return;
        }
        Map<String, String> checkboxKeys = analyzerClient.extractCheckboxKeys(html);
        Set<String> alreadySent = processedSamplesStore.loadSentSampleIds();

        Set<String> seenThisRun = new LinkedHashSet<>();
        for (String sampleId : sampleIds) {
            if (!seenThisRun.add(sampleId)) {
                continue;
            }
            if (alreadySent.contains(sampleId)) {
                log.debug("Sample {} already sent to the LIS, skipping", sampleId);
                continue;
            }
            processSample(sampleId, checkboxKeys);
        }
    }

    private void processSample(String sampleId, Map<String, String> checkboxKeys) {
        String checkboxKey = checkboxKeys.get(sampleId);
        if (checkboxKey == null) {
            log.warn("No report key found for sample {}, cannot fetch patient report", sampleId);
            return;
        }

        String pdfUrl = analyzerClient.buildPatientReportPdfUrl(checkboxKey);
        byte[] pdfBytes = analyzerClient.fetchBytes(pdfUrl);
        if (pdfBytes == null) {
            log.warn("Could not download patient report PDF for sample {}", sampleId);
            return;
        }

        PatientReportData data = reportParser.parse(pdfBytes, sampleId);
        String issuedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date());

        boolean primarySent = sendPrimaryA1cResult(sampleId, data, issuedDate);

        if (config.sendPeakTable) {
            sendPeakTable(sampleId, data, issuedDate);
        }

        if (config.sendReportHeader) {
            sendReportHeader(sampleId, data, issuedDate);
        }

        sendChromatogram(sampleId, data, issuedDate);

        if (primarySent) {
            processedSamplesStore.markSent(sampleId);
            log.info("Sample {} sent to the LIS successfully", sampleId);
        } else {
            log.warn("Sample {} will be retried on next poll (primary A1c result was not accepted)", sampleId);
        }
    }

    private boolean sendPrimaryA1cResult(String sampleId, PatientReportData data, String issuedDate) {
        Double a1c = data.resolveA1cPercent();
        if (a1c == null) {
            log.warn("No A1c concentration could be determined for sample {}; skipping primary result", sampleId);
            return false;
        }
        JSONObject obs = limsClient.buildObservationJson(sampleId, String.valueOf(a1c),
                "http://loinc.org", "4548-4",
                "http://unitsofmeasure.org", "%", issuedDate);
        boolean sent = limsClient.send(obs);
        if (sent) {
            auditLog.recordSent(sampleId, "LOINC:4548-4:A1c%", String.valueOf(a1c));
        }
        return sent;
    }

    private void sendPeakTable(String sampleId, PatientReportData data, String issuedDate) {
        String codingSystem = config.peakObservationCodingSystem;

        for (PeakResult peak : data.getPeaks()) {
            String name = peak.getPeakName();
            sendPeakComponent(sampleId, codingSystem, name, "TIME", peak.getRetentionTimeMinutes(), "min", issuedDate);
            sendPeakComponent(sampleId, codingSystem, name, "HEIGHT", peak.getHeight(), "counts", issuedDate);
            sendPeakComponent(sampleId, codingSystem, name, "AREA", peak.getRawArea(), "counts", issuedDate);
            sendPeakComponent(sampleId, codingSystem, name, "AREAPCT", peak.getAreaPercent(), "%", issuedDate);
        }

        if (data.getTotalArea() != null) {
            sendPeakComponent(sampleId, codingSystem, "TOTAL", "AREA", data.getTotalArea(), "counts", issuedDate);
        }
    }

    private void sendPeakComponent(String sampleId, String codingSystem, String peakName, String component,
                                    Object value, String unit, String issuedDate) {
        if (value == null) {
            return;
        }
        String code = peakName + "^" + component;
        JSONObject obs = limsClient.buildObservationJson(sampleId, String.valueOf(value),
                codingSystem, code, "http://unitsofmeasure.org", unit, issuedDate);
        boolean sent = limsClient.send(obs);
        if (sent) {
            auditLog.recordSent(sampleId, codingSystem + ":" + code, String.valueOf(value));
        }
    }

    private void sendReportHeader(String sampleId, PatientReportData data, String issuedDate) {
        ReportHeader header = data.getReportHeader();
        if (header == null || header.isEmpty()) {
            return;
        }
        String codingSystem = config.headerObservationCodingSystem;

        sendHeaderField(sampleId, codingSystem, "INJECTION_DATE", header.getInjectionDateTime(), issuedDate);
        sendHeaderField(sampleId, codingSystem, "INJECTION_NUMBER", header.getInjectionNumber(), issuedDate);
        sendHeaderField(sampleId, codingSystem, "RACK_NUMBER", header.getRackNumber(), issuedDate);
        sendHeaderField(sampleId, codingSystem, "RACK_POSITION", header.getRackPosition(), issuedDate);
        sendHeaderField(sampleId, codingSystem, "METHOD", header.getMethod(), issuedDate);
        sendHeaderField(sampleId, codingSystem, "SERIAL_NUMBER", header.getInstrumentSerialNumber(), issuedDate);
        sendHeaderField(sampleId, codingSystem, "SOFTWARE_VERSION", header.getSoftwareVersion(), issuedDate);
    }

    private void sendHeaderField(String sampleId, String codingSystem, String code, String value, String issuedDate) {
        if (value == null || value.isEmpty()) {
            return;
        }
        JSONObject obs = limsClient.buildObservationJson(sampleId, value,
                codingSystem, code, "", "", issuedDate);
        boolean sent = limsClient.send(obs);
        if (sent) {
            auditLog.recordSent(sampleId, codingSystem + ":" + code, value);
        }
    }

    private void sendChromatogram(String sampleId, PatientReportData data, String issuedDate) {
        if (config.chromatogramObservationCode == null || config.chromatogramObservationCode.isEmpty()) {
            return;
        }
        byte[] png = data.getChromatogramPng();
        if (png == null || png.length == 0) {
            log.warn("No chromatogram image extracted for sample {}", sampleId);
            return;
        }

        String base64 = Base64.getEncoder().encodeToString(png);
        String imageValue = "^Image^PNG^Base64^" + base64;

        JSONObject imgObs = limsClient.buildObservationJson(sampleId, imageValue,
                config.chromatogramObservationCodeSystem, config.chromatogramObservationCode,
                "", "", issuedDate);

        log.info("Sending chromatogram observation for sample {} ({} bytes)", sampleId, png.length);
        boolean sent = limsClient.send(imgObs);
        if (sent) {
            auditLog.recordSent(sampleId, config.chromatogramObservationCodeSystem + ":" + config.chromatogramObservationCode,
                    "[PNG image, " + png.length + " bytes]");
        }
    }
}
