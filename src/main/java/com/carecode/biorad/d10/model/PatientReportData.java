package com.carecode.biorad.d10.model;

import java.util.Collections;
import java.util.List;

/**
 * Everything extracted from one D-10 "Patient report" PDF for a single sample:
 * the chromatogram chart image, the peak table, the total area and the
 * reported HbA1c concentration.
 */
public class PatientReportData {

    private final String sampleId;
    private final Long totalArea;
    private final Double concentrationA1cPercent;
    private final List<PeakResult> peaks;
    private final byte[] chromatogramPng;

    public PatientReportData(String sampleId, Long totalArea, Double concentrationA1cPercent,
                              List<PeakResult> peaks, byte[] chromatogramPng) {
        this.sampleId = sampleId;
        this.totalArea = totalArea;
        this.concentrationA1cPercent = concentrationA1cPercent;
        this.peaks = peaks == null ? Collections.emptyList() : peaks;
        this.chromatogramPng = chromatogramPng;
    }

    public String getSampleId() {
        return sampleId;
    }

    public Long getTotalArea() {
        return totalArea;
    }

    public Double getConcentrationA1cPercent() {
        return concentrationA1cPercent;
    }

    public List<PeakResult> getPeaks() {
        return peaks;
    }

    public byte[] getChromatogramPng() {
        return chromatogramPng;
    }

    /**
     * The reported A1c% falls back to the A1c peak's Area% column when the
     * report's own "Concentration" box could not be parsed.
     */
    public Double resolveA1cPercent() {
        if (concentrationA1cPercent != null) {
            return concentrationA1cPercent;
        }
        return peaks.stream()
                .filter(p -> "A1c".equalsIgnoreCase(p.getPeakName()))
                .map(PeakResult::getAreaPercent)
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }
}
