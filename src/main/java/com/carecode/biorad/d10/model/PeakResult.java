package com.carecode.biorad.d10.model;

/**
 * One row of the D-10 "Peak table" (Peak | R.time | Height | Area | Area %).
 * Field names follow the ASTM LIS2-A component naming used in the Bio-Rad D-10
 * LIS Interface Requirements manual: peakName^TIME and peakName^AREA, where
 * "AREA" in the ASTM sense is the Area% column (concentration in area percent).
 * Height and the raw Area (peak counts) are extra fields the manual does not
 * define but the printed patient report includes, so they are carried here too.
 */
public class PeakResult {

    private final String peakName;
    private final Double retentionTimeMinutes;
    private final Long height;
    private final Long rawArea;
    private final Double areaPercent;

    public PeakResult(String peakName, Double retentionTimeMinutes, Long height, Long rawArea, Double areaPercent) {
        this.peakName = peakName;
        this.retentionTimeMinutes = retentionTimeMinutes;
        this.height = height;
        this.rawArea = rawArea;
        this.areaPercent = areaPercent;
    }

    public String getPeakName() {
        return peakName;
    }

    public Double getRetentionTimeMinutes() {
        return retentionTimeMinutes;
    }

    public Long getHeight() {
        return height;
    }

    public Long getRawArea() {
        return rawArea;
    }

    public Double getAreaPercent() {
        return areaPercent;
    }

    @Override
    public String toString() {
        return "PeakResult{" + peakName + " rtime=" + retentionTimeMinutes
                + " height=" + height + " area=" + rawArea + " area%=" + areaPercent + "}";
    }
}
