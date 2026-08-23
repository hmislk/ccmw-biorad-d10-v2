package com.carecode.biorad.d10.config;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Middleware configuration, loaded from a JSON file. See config.json.example
 * at the project root for the full set of keys.
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public String analyzerBaseURL;
    public int queryFrequencyInMinutes = 1;
    public boolean queryForYesterdayResults = true;

    public String limsServerBaseUrl;
    public String username;
    public String password;

    public String departmentId;
    public String analyzerId;
    public String departmentAnalyzerId;
    public String analyzerName;

    public String chromatogramObservationCodeSystem = "D10-IMG";
    public String chromatogramObservationCode = "D10-CHROMATOGRAM";

    public String peakObservationCodingSystem = "D10-PEAK";
    public boolean sendPeakTable = true;

    public String headerObservationCodingSystem = "D10-META";
    public boolean sendReportHeader = true;

    public String processedSamplesDirectory = "state";

    public boolean disableSslVerification = false;

    public static AppConfig load(String configFilePath) throws IOException {
        log.info("Loading configuration from file: {}", configFilePath);
        String content = new String(Files.readAllBytes(Paths.get(configFilePath)));
        JSONObject root = new JSONObject(content);
        JSONObject mw = root.getJSONObject("middlewareSettings");
        JSONObject analyzerDetails = mw.getJSONObject("analyzerDetails");
        JSONObject communication = mw.getJSONObject("communication");
        JSONObject limsSettings = mw.getJSONObject("limsSettings");

        AppConfig cfg = new AppConfig();
        cfg.analyzerBaseURL = analyzerDetails.getString("analyzerBaseURL");
        cfg.departmentId = analyzerDetails.getString("departmentId");
        cfg.analyzerName = analyzerDetails.getString("analyzerName");
        cfg.analyzerId = analyzerDetails.getString("analyzerId");
        cfg.departmentAnalyzerId = analyzerDetails.getString("departmentAnalyzerId");

        cfg.queryFrequencyInMinutes = communication.getInt("queryFrequencyInMinutes");
        cfg.queryForYesterdayResults = communication.getBoolean("queryForYesterdayResults");

        cfg.limsServerBaseUrl = limsSettings.getString("limsServerBaseUrl");
        cfg.username = limsSettings.getString("username");
        cfg.password = limsSettings.getString("password");

        if (mw.has("chromatogramObservationCodeSystem")) {
            cfg.chromatogramObservationCodeSystem = mw.getString("chromatogramObservationCodeSystem");
        }
        if (mw.has("chromatogramObservationCode")) {
            cfg.chromatogramObservationCode = mw.getString("chromatogramObservationCode");
        } else if (mw.has("chromatogramTestCode")) {
            cfg.chromatogramObservationCode = mw.getString("chromatogramTestCode");
        }
        if (mw.has("peakObservationCodingSystem")) {
            cfg.peakObservationCodingSystem = mw.getString("peakObservationCodingSystem");
        }
        if (mw.has("sendPeakTable")) {
            cfg.sendPeakTable = mw.getBoolean("sendPeakTable");
        }
        if (mw.has("headerObservationCodingSystem")) {
            cfg.headerObservationCodingSystem = mw.getString("headerObservationCodingSystem");
        }
        if (mw.has("sendReportHeader")) {
            cfg.sendReportHeader = mw.getBoolean("sendReportHeader");
        }
        if (mw.has("processedSamplesDirectory")) {
            cfg.processedSamplesDirectory = mw.getString("processedSamplesDirectory");
        }
        if (mw.has("disableSslVerification")) {
            cfg.disableSslVerification = mw.getBoolean("disableSslVerification");
        }

        log.info("Configuration loaded successfully");
        return cfg;
    }
}
