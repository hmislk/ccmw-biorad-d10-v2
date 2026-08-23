package com.carecode.biorad.d10.analyzer;

import com.carecode.biorad.d10.model.PeakResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeakTableTextParserTest {

    // Transcribed from the analyzer's printed "Patient report" for sample 10170973-DAYANI.
    private static final String SAMPLE_REPORT_TEXT =
            "Patient report\n"
            + "Sample ID: 10170973-DAYANI\n"
            + "Injection date 08/20/2026 05:14 PM\n"
            + "Peak table - ID: 10170973-DAYANI\n"
            + "Peak R.time Height Area Area %\n"
            + "A1a 0.18 1593 6570 0.6\n"
            + "A1b 0.30 4778 32050 3.0\n"
            + "LA1c/CHb-1 0.65 4788 38665 3.7\n"
            + "A1c 0.85 10550 89615 10.7\n"
            + "P3 1.32 18359 79340 7.5\n"
            + "A0 1.43 322033 805113 76.6\n"
            + "Total Area: 1051354\n"
            + "Concentration: % A1c 10.7\n";

    @Test
    void parsesAllSixPeakRows() {
        PeakTableTextParser.Result result = PeakTableTextParser.parse(SAMPLE_REPORT_TEXT);

        assertEquals(6, result.peaks.size());
        PeakResult a1c = findPeak(result.peaks, "A1c");
        assertNotNull(a1c);
        assertEquals(0.85, a1c.getRetentionTimeMinutes());
        assertEquals(10550L, a1c.getHeight());
        assertEquals(89615L, a1c.getRawArea());
        assertEquals(10.7, a1c.getAreaPercent());
    }

    @Test
    void doesNotConfuseLA1cCHb1WithA1c() {
        PeakTableTextParser.Result result = PeakTableTextParser.parse(SAMPLE_REPORT_TEXT);

        PeakResult labileA1c = findPeak(result.peaks, "LA1c/CHb-1");
        assertNotNull(labileA1c);
        assertEquals(3.7, labileA1c.getAreaPercent());

        // Exactly one A1c row, not a spurious second match inside "LA1c/CHb-1".
        long a1cCount = result.peaks.stream().filter(p -> p.getPeakName().equals("A1c")).count();
        assertEquals(1, a1cCount);
    }

    @Test
    void parsesTotalAreaAndConcentration() {
        PeakTableTextParser.Result result = PeakTableTextParser.parse(SAMPLE_REPORT_TEXT);

        assertEquals(1051354L, result.totalArea);
        assertEquals(10.7, result.concentrationA1cPercent);
    }

    @Test
    void returnsEmptyResultForBlankText() {
        PeakTableTextParser.Result result = PeakTableTextParser.parse("");
        assertTrue(result.peaks.isEmpty());
        assertEquals(null, result.totalArea);
        assertEquals(null, result.concentrationA1cPercent);
    }

    private static PeakResult findPeak(List<PeakResult> peaks, String name) {
        return peaks.stream().filter(p -> p.getPeakName().equals(name)).findFirst().orElse(null);
    }
}
