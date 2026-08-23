package com.carecode.biorad.d10.model;

/**
 * The run/instrument metadata printed at the top of a D-10 "Patient report",
 * above the peak table -- injection date/time, injection #, rack #, rack
 * position, method, instrument serial number and Bio-Rad software version.
 * Any field the report text didn't contain (or that {@link
 * com.carecode.biorad.d10.analyzer.ReportHeaderTextParser} couldn't match) is
 * left null rather than blocking extraction of the rest.
 */
public class ReportHeader {

    private final String injectionDateTime;
    private final String injectionNumber;
    private final String rackNumber;
    private final String rackPosition;
    private final String method;
    private final String instrumentSerialNumber;
    private final String softwareVersion;

    public ReportHeader(String injectionDateTime, String injectionNumber, String rackNumber,
                         String rackPosition, String method, String instrumentSerialNumber,
                         String softwareVersion) {
        this.injectionDateTime = injectionDateTime;
        this.injectionNumber = injectionNumber;
        this.rackNumber = rackNumber;
        this.rackPosition = rackPosition;
        this.method = method;
        this.instrumentSerialNumber = instrumentSerialNumber;
        this.softwareVersion = softwareVersion;
    }

    public String getInjectionDateTime() {
        return injectionDateTime;
    }

    public String getInjectionNumber() {
        return injectionNumber;
    }

    public String getRackNumber() {
        return rackNumber;
    }

    public String getRackPosition() {
        return rackPosition;
    }

    public String getMethod() {
        return method;
    }

    public String getInstrumentSerialNumber() {
        return instrumentSerialNumber;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public boolean isEmpty() {
        return injectionDateTime == null && injectionNumber == null && rackNumber == null
                && rackPosition == null && method == null && instrumentSerialNumber == null
                && softwareVersion == null;
    }
}
