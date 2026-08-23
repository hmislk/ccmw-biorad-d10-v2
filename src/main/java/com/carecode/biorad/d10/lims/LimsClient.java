package com.carecode.biorad.d10.lims;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Sends observation JSON payloads to the hospital LIMS REST endpoint
 * ({limsServerBaseUrl}/observation, HTTP Basic Auth), the same integration
 * style used by the referring middleware.
 */
public class LimsClient {

    private static final Logger log = LoggerFactory.getLogger(LimsClient.class);

    private final String limsServerBaseUrl;
    private final String username;
    private final String password;
    private final String analyzerId;
    private final String departmentAnalyzerId;
    private final String analyzerName;
    private final String departmentId;

    public LimsClient(String limsServerBaseUrl, String username, String password,
                       String analyzerId, String departmentAnalyzerId, String analyzerName, String departmentId) {
        this.limsServerBaseUrl = limsServerBaseUrl;
        this.username = username;
        this.password = password;
        this.analyzerId = analyzerId;
        this.departmentAnalyzerId = departmentAnalyzerId;
        this.analyzerName = analyzerName;
        this.departmentId = departmentId;
    }

    public JSONObject buildObservationJson(String sampleId, String value,
                                            String valueCodingSystem, String valueCode,
                                            String unitCodingSystem, String unitCode,
                                            String issuedDate) {
        JSONObject obs = new JSONObject();
        obs.put("sampleId", sampleId);
        obs.put("observationValue", value);
        obs.put("analyzerId", analyzerId);
        obs.put("departmentAnalyzerId", departmentAnalyzerId);
        obs.put("analyzerName", analyzerName);
        obs.put("departmentId", departmentId);
        obs.put("username", username);
        obs.put("password", password);
        obs.put("issuedDate", issuedDate);
        obs.put("observationValueCodingSystem", valueCodingSystem);
        obs.put("observationValueCode", valueCode);
        obs.put("observationUnitCodingSystem", unitCodingSystem);
        obs.put("observationUnitCode", unitCode);
        return obs;
    }

    public boolean send(JSONObject observationJson) {
        String sampleId = observationJson.optString("sampleId", "?");
        String code = observationJson.optString("observationValueCode", "?");
        log.info("Sending observation to LIMS: sample={} code={}", sampleId, code);
        try {
            URL url = new URL(limsServerBaseUrl + "/observation");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(observationJson.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            log.debug("LIMS response code for sample={} code={}: {}", sampleId, code, responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                log.info("LIMS accepted observation: sample={} code={} (HTTP {})", sampleId, code, responseCode);
                return true;
            } else {
                logErrorBody(connection, sampleId, code, responseCode);
                return false;
            }
        } catch (IOException e) {
            log.error("Exception sending observation to LIMS: sample={} code={}", sampleId, code, e);
            return false;
        }
    }

    private void logErrorBody(HttpURLConnection connection, String sampleId, String code, int responseCode) {
        try (InputStream errStream = connection.getErrorStream()) {
            if (errStream == null) {
                log.error("LIMS rejected observation: sample={} code={} (HTTP {})", sampleId, code, responseCode);
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
            log.error("LIMS rejected observation: sample={} code={} (HTTP {}): {}",
                    sampleId, code, responseCode, body);
        } catch (IOException e) {
            log.error("LIMS rejected observation and error body could not be read: sample={} code={} (HTTP {})",
                    sampleId, code, responseCode, e);
        }
    }
}
