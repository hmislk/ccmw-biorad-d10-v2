package com.carecode.biorad.d10.analyzer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operators enter the Sample ID at the analyzer together with the patient's
 * name, using whichever separator is at hand, e.g. "1258968_Damith",
 * "1458962-Damith", "125486 Damith" -- or, on the analyzer's own printed
 * reports, "10170973-DAYANI". Only the ID before the first separator is a
 * real specimen identifier; the rest is a name and must never be sent to
 * the LIS as (or as part of) the sample ID.
 *
 * Control samples such as "A1CTRH" have no separator/name at all and pass
 * through unchanged.
 */
public final class SampleIdParser {

    private static final Pattern SEPARATOR = Pattern.compile("[_\\-\\s]");

    private SampleIdParser() {
    }

    public static String extractSampleId(String rawSampleField) {
        if (rawSampleField == null) {
            return null;
        }
        String trimmed = rawSampleField.trim();
        Matcher m = SEPARATOR.matcher(trimmed);
        if (m.find() && m.start() > 0) {
            return trimmed.substring(0, m.start());
        }
        return trimmed;
    }
}
