package com.carecode.biorad.d10.analyzer;

import com.carecode.biorad.d10.model.ReportHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the run/instrument header block of a D-10 Patient report (Injection
 * date, Injection #, Rack #, Rack position, Method, S/N, Bio-Rad software
 * version). Pure text in, data out -- no PDF or network dependency, same
 * approach as {@link PeakTableTextParser}. Each field is matched
 * independently: a field the report doesn't print (or that this parser
 * doesn't recognize the wording for) is simply left null rather than
 * blocking the others.
 */
public final class ReportHeaderTextParser {

    private static final Logger log = LoggerFactory.getLogger(ReportHeaderTextParser.class);

    private static final Pattern INJECTION_DATE_PATTERN = Pattern.compile(
            "Injection\\s*date\\s*:?\\s*([0-9]{1,2}[/\\-][0-9]{1,2}[/\\-][0-9]{2,4}"
                    + "(?:[ ,]+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INJECTION_NUMBER_PATTERN = Pattern.compile(
            "Injection\\s*#\\s*:?\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern RACK_POSITION_PATTERN = Pattern.compile(
            "Rack\\s*position\\s*:?\\s*([0-9A-Za-z\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern RACK_NUMBER_PATTERN = Pattern.compile(
            "Rack\\s*#\\s*:?\\s*([0-9A-Za-z\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "Method\\s*:?\\s*([A-Za-z0-9 _\\-/.]+?)\\s*(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SERIAL_NUMBER_PATTERN = Pattern.compile(
            "S\\s*/\\s*N\\s*:?\\s*([A-Za-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SOFTWARE_VERSION_PATTERN = Pattern.compile(
            "Software\\s+[Vv]ersion\\s*:?\\s*([0-9]+(?:\\.[0-9]+)*)", Pattern.CASE_INSENSITIVE);

    private ReportHeaderTextParser() {
    }

    public static ReportHeader parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ReportHeader(null, null, null, null, null, null, null);
        }

        ReportHeader header = new ReportHeader(
                find(INJECTION_DATE_PATTERN, text),
                find(INJECTION_NUMBER_PATTERN, text),
                find(RACK_NUMBER_PATTERN, text),
                find(RACK_POSITION_PATTERN, text),
                find(METHOD_PATTERN, text),
                find(SERIAL_NUMBER_PATTERN, text),
                find(SOFTWARE_VERSION_PATTERN, text));

        if (header.isEmpty()) {
            log.warn("No report header fields matched in report text");
        }
        return header;
    }

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}
