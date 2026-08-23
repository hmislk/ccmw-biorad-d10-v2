package com.carecode.biorad.d10;

import com.carecode.biorad.d10.analyzer.AnalyzerClient;
import com.carecode.biorad.d10.analyzer.PatientReportParser;
import com.carecode.biorad.d10.config.AppConfig;
import com.carecode.biorad.d10.lims.LimsClient;
import com.carecode.biorad.d10.lims.ResultsAuditLog;
import com.carecode.biorad.d10.processing.SampleProcessor;
import com.carecode.biorad.d10.tracking.ProcessedSamplesStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Entry point: loads config, wires up the analyzer/LIMS/logging components,
 * runs one polling cycle immediately, then repeats on a timer.
 */
public class D10Middleware {

    private static final Logger log = LoggerFactory.getLogger(D10Middleware.class);

    public static void main(String[] args) {
        log.info("D-10 middleware starting");

        String configPath = args.length > 0 ? args[0] : "config.json";

        AppConfig config;
        try {
            config = AppConfig.load(configPath);
        } catch (Exception e) {
            log.error("Failed to load configuration from {}", configPath, e);
            return;
        }

        AnalyzerClient analyzerClient = new AnalyzerClient(config.analyzerBaseURL, config.disableSslVerification);
        PatientReportParser reportParser = new PatientReportParser();
        LimsClient limsClient = new LimsClient(config.limsServerBaseUrl, config.username, config.password,
                config.analyzerId, config.departmentAnalyzerId, config.analyzerName, config.departmentId);
        ResultsAuditLog auditLog = new ResultsAuditLog();
        ProcessedSamplesStore processedSamplesStore = new ProcessedSamplesStore(config.processedSamplesDirectory);

        SampleProcessor processor = new SampleProcessor(config, analyzerClient, reportParser, limsClient,
                auditLog, processedSamplesStore);

        Runnable poll = () -> runPollCycle(processor, config);
        poll.run();

        Timer timer = new Timer("d10-poll-timer");
        long periodMs = config.queryFrequencyInMinutes * 60 * 1000L;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                poll.run();
            }
        }, periodMs, periodMs);

        log.info("D-10 middleware running, polling every {} minute(s)", config.queryFrequencyInMinutes);
    }

    private static void runPollCycle(SampleProcessor processor, AppConfig config) {
        try {
            LocalDate today = LocalDate.now();
            processor.processDate(today);

            if (config.queryForYesterdayResults) {
                processor.processDate(today.minusDays(1));
            }
        } catch (Exception e) {
            log.error("Unhandled exception during poll cycle", e);
        }
    }
}
