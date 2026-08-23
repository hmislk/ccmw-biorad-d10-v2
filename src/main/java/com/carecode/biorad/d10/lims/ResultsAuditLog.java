package com.carecode.biorad.d10.lims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daily audit trail of every result actually accepted by the LIS. Backed by
 * the "resultsSent" logger, which logback.xml routes to a fresh file each
 * day under logs/results-sent/. Kept separate from the general application
 * log so it reads as a clean, parseable record of what was sent.
 */
public class ResultsAuditLog {

    private static final Logger resultsSent = LoggerFactory.getLogger("resultsSent");

    public void recordSent(String sampleId, String observationCode, String displayValue) {
        resultsSent.info("SENT\tsample={}\tcode={}\tvalue={}", sampleId, observationCode, displayValue);
    }
}
