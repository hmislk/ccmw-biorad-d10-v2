package com.carecode.biorad.d10.analyzer;

import com.carecode.biorad.d10.model.PeakResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the "Peak table" text block of a D-10 Patient report (Peak | R.time | Height | Area | Area %)
 * plus the Total Area and Concentration lines. Pure text in, data out -- no PDF or network
 * dependency, so this is unit-testable against text captured from a real report.
 *
 * Peak names are the same vocabulary the Bio-Rad LIS Interface Requirements manual uses for
 * Result records: A1a, A1b, F, LA1c/CHb-1, A1c, P3, A0, plus the less common P1/P2/P4/P5,
 * E/D/S/C variants and Unknown.
 */
public final class PeakTableTextParser {

    private static final Logger log = LoggerFactory.getLogger(PeakTableTextParser.class);

    private static final String[] KNOWN_PEAK_NAMES = {
            "LA1c/CHb-1", "A1a", "A1b", "A1c", "A0", "P1", "P2", "P3", "P4", "P5",
            "F", "E", "D", "S", "C", "Variant", "Unknown"
    };

    private static final Pattern TOTAL_AREA_PATTERN =
            Pattern.compile("Total\\s*Area\\s*:?\\s*([0-9][0-9,]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CONCENTRATION_PATTERN =
            Pattern.compile("Concentration.{0,40}?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private PeakTableTextParser() {
    }

    public static Result parse(String text) {
        List<PeakResult> peaks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            log.warn("No text available to parse peak table from");
            return new Result(peaks, null, null);
        }

        Set<String> matchedSpans = new LinkedHashSet<>();
        for (String peakName : KNOWN_PEAK_NAMES) {
            Pattern rowPattern = Pattern.compile(
                    "\\b" + Pattern.quote(peakName) + "\\b\\s+"
                            + "([0-9]+\\.[0-9]+)\\s+"      // R.time
                            + "([0-9][0-9,]*)\\s+"          // Height
                            + "([0-9][0-9,]*)\\s+"          // Area
                            + "([0-9]+\\.[0-9]+)"           // Area %
            );
            Matcher m = rowPattern.matcher(text);
            while (m.find()) {
                String span = m.start() + ":" + m.end();
                if (!matchedSpans.add(span)) {
                    continue;
                }
                try {
                    double rTime = Double.parseDouble(m.group(1));
                    long height = Long.parseLong(m.group(2).replace(",", ""));
                    long area = Long.parseLong(m.group(3).replace(",", ""));
                    double areaPct = Double.parseDouble(m.group(4));
                    peaks.add(new PeakResult(peakName, rTime, height, area, areaPct));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse peak row for {}: {}", peakName, m.group());
                }
            }
        }

        Long totalArea = null;
        Matcher totalMatcher = TOTAL_AREA_PATTERN.matcher(text);
        if (totalMatcher.find()) {
            totalArea = Long.parseLong(totalMatcher.group(1).replace(",", ""));
        }

        Double concentration = null;
        Matcher concMatcher = CONCENTRATION_PATTERN.matcher(text);
        if (concMatcher.find()) {
            concentration = Double.parseDouble(concMatcher.group(1));
        }

        if (peaks.isEmpty()) {
            log.warn("No peak rows matched in report text; peak table will not be sent to LIS");
        }

        return new Result(peaks, totalArea, concentration);
    }

    public static final class Result {
        public final List<PeakResult> peaks;
        public final Long totalArea;
        public final Double concentrationA1cPercent;

        public Result(List<PeakResult> peaks, Long totalArea, Double concentrationA1cPercent) {
            this.peaks = peaks;
            this.totalArea = totalArea;
            this.concentrationA1cPercent = concentrationA1cPercent;
        }
    }
}
