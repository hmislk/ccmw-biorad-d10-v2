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
 * Sometimes operator initials are typed directly after the numeric ID with
 * no separator at all, e.g. "10171633MH" -- only the leading digits are the
 * real specimen identifier there too.
 *
 * Control samples such as "A1CTRH" have no separator/name at all and pass
 * through unchanged.
 */
public final class SampleIdParser {

    private static final Pattern SEPARATOR = Pattern.compile("[_\\-\\s]");
    private static final Pattern DIGITS_THEN_LETTERS = Pattern.compile("^(\\d+)[A-Za-z]+$");

    private SampleIdParser() {
    }

    /**
     * "RACK" is the D-10's own rack-check/calibration entry, not a specimen
     * -- no real HMIS sample ID is ever this literal word. Distinct from
     * control samples like "A1CTRH", which are real, intentionally sent
     * specimens.
     */
    public static boolean isNonPatientSample(String sampleId) {
        return sampleId != null && "RACK".equalsIgnoreCase(sampleId.trim());
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
        Matcher digitsThenLetters = DIGITS_THEN_LETTERS.matcher(trimmed);
        if (digitsThenLetters.matches()) {
            return digitsThenLetters.group(1);
        }
        return trimmed;
    }
}
