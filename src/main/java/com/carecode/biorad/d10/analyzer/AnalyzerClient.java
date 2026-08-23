package com.carecode.biorad.d10.analyzer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to the D-10's built-in web interface (DiaWeb.cgi): fetches the daily
 * results HTML page, the per-sample checkbox keys needed to build a PDF report
 * URL, and downloads the PDF report bytes themselves.
 */
public class AnalyzerClient {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerClient.class);

    private final String analyzerBaseURL;

    public AnalyzerClient(String analyzerBaseURL, boolean disableSslVerification) {
        this.analyzerBaseURL = analyzerBaseURL;
        if (disableSslVerification) {
            trustAllCertificates();
        }
    }

    public String generateUrlForDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String dateStr = date.format(formatter);
        String url = analyzerBaseURL + "?page=result&test=HBA1C&StartDate="
                + dateStr.replace("/", "%2F") + "&EndDate=" + dateStr.replace("/", "%2F");
        log.debug("Generated results URL: {}", url);
        return url;
    }

    public String fetchHtmlContent(String urlString) {
        log.info("Fetching HTML content from URL: {}", urlString);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            int responseCode = connection.getResponseCode();
            log.debug("Response Code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder htmlContent = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        htmlContent.append(inputLine);
                    }
                    return htmlContent.toString();
                }
            } else {
                log.error("Failed to fetch HTML content. HTTP Response Code: {}", responseCode);
                return null;
            }
        } catch (ConnectException e) {
            log.error("Cannot reach analyzer at {}", urlString, e);
            return null;
        } catch (IOException e) {
            log.error("IOException fetching {}", urlString, e);
            return null;
        }
    }

    /**
     * Sample IDs present on the daily results page, in the order the analyzer listed them.
     */
    public List<String> extractSampleIds(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            log.info("No response from analyzer");
            return Collections.emptyList();
        }
        List<String> sampleIds = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements rows = doc.select("table tr");
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() > 5) {
                    sampleIds.add(cells.get(3).text());
                }
            }
        } catch (Exception e) {
            log.error("Exception extracting sample list", e);
        }
        return sampleIds;
    }

    /**
     * sampleId -> checkbox "value" attribute, needed to build the per-sample PDF report URL.
     */
    public Map<String, String> extractCheckboxKeys(String htmlContent) {
        Map<String, String> keys = new LinkedHashMap<>();
        if (htmlContent == null || htmlContent.isEmpty()) {
            return keys;
        }
        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements checkboxes = doc.select("input[type=checkbox][name!=_allbox]");
            for (Element cb : checkboxes) {
                String val = cb.attr("value").trim();
                if (!val.isEmpty()) {
                    String sampleId = val.contains(" ") ? val.substring(0, val.indexOf(' ')) : val;
                    keys.put(sampleId, val);
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting checkbox keys", e);
        }
        return keys;
    }

    public String buildPatientReportPdfUrl(String checkboxKey) {
        String encodedKey = checkboxKey.replace(" ", "+");
        return analyzerBaseURL + "?page=pdf&test=HBA1C&nbfile=1&f0=" + encodedKey;
    }

    public byte[] fetchBytes(String urlString) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.warn("HTTP {} fetching: {}", conn.getResponseCode(), urlString);
                return null;
            }
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                return baos.toByteArray();
            }
        } catch (ConnectException e) {
            log.warn("Cannot reach analyzer: {}", e.getMessage());
            return null;
        } catch (IOException e) {
            log.warn("Error fetching bytes: {}", urlString, e);
            return null;
        }
    }

    private static void trustAllCertificates() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] c, String a) {
                        }

                        public void checkServerTrusted(X509Certificate[] c, String a) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            log.warn("SSL verification DISABLED for analyzer connections -- development use only");
        } catch (Exception e) {
            log.error("Failed to disable SSL verification", e);
        }
    }
}
