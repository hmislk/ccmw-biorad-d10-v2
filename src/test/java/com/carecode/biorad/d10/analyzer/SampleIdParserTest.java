package com.carecode.biorad.d10.analyzer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleIdParserTest {

    @Test
    void stripsPatientNameAfterUnderscore() {
        assertEquals("1258968", SampleIdParser.extractSampleId("1258968_Damith"));
    }

    @Test
    void stripsPatientNameAfterHyphen() {
        assertEquals("1458962", SampleIdParser.extractSampleId("1458962-Damith"));
    }

    @Test
    void stripsPatientNameAfterSpace() {
        assertEquals("125486", SampleIdParser.extractSampleId("125486 Damith"));
    }

    @Test
    void stripsHyphenatedNameFromRealPrintedSampleId() {
        assertEquals("10170973", SampleIdParser.extractSampleId("10170973-DAYANI"));
        assertEquals("10170955", SampleIdParser.extractSampleId("10170955-N.RAJITHA"));
    }

    @Test
    void leavesControlSampleIdsWithNoNameUnchanged() {
        assertEquals("A1CTRH", SampleIdParser.extractSampleId("A1CTRH"));
        assertEquals("591318", SampleIdParser.extractSampleId("591318"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("591318", SampleIdParser.extractSampleId("  591318  "));
    }
}
