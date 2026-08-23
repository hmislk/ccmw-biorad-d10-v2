package com.carecode.biorad.d10.analyzer;

import com.carecode.biorad.d10.model.ReportHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportHeaderTextParserTest {

    @Test
    void parsesAllHeaderFields() {
        String text = "Patient report\n"
                + "Sample ID: 10170973-DAYANI\n"
                + "Injection date: 08/20/2026 14:32:05\n"
                + "Injection #: 128\n"
                + "Rack #: 4\n"
                + "Rack position: 6\n"
                + "Method: HbA1c dual\n"
                + "S/N: 12345-ABCDE\n"
                + "Bio Rad D-10 Software Version 3.10\n";

        ReportHeader header = ReportHeaderTextParser.parse(text);

        assertEquals("08/20/2026 14:32:05", header.getInjectionDateTime());
        assertEquals("128", header.getInjectionNumber());
        assertEquals("4", header.getRackNumber());
        assertEquals("6", header.getRackPosition());
        assertEquals("HbA1c dual", header.getMethod());
        assertEquals("12345-ABCDE", header.getInstrumentSerialNumber());
        assertEquals("3.10", header.getSoftwareVersion());
        assertTrue(!header.isEmpty());
    }

    @Test
    void leavesMissingFieldsNullWithoutFailing() {
        String text = "Injection #: 42\n";

        ReportHeader header = ReportHeaderTextParser.parse(text);

        assertEquals("42", header.getInjectionNumber());
        assertNull(header.getInjectionDateTime());
        assertNull(header.getRackNumber());
        assertNull(header.getMethod());
        assertNull(header.getInstrumentSerialNumber());
        assertNull(header.getSoftwareVersion());
    }

    @Test
    void returnsEmptyHeaderForBlankText() {
        ReportHeader header = ReportHeaderTextParser.parse("");
        assertTrue(header.isEmpty());
    }
}
