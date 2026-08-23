package com.carecode.biorad.d10.analyzer;

import com.carecode.biorad.d10.model.PatientReportData;
import com.carecode.biorad.d10.model.ReportHeader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Turns the bytes of a D-10 "Patient report" PDF into structured data: the
 * chromatogram chart (as a PNG, extracted from the embedded image object)
 * and the peak table / total area / concentration (extracted as text).
 *
 * If the report PDF turns out to be a single flattened raster page (no
 * selectable text layer), text extraction yields nothing and the peak table
 * is simply omitted from that sample's data -- the chromatogram image and
 * the primary A1c result are unaffected. That would need OCR to fix, which
 * this parser does not attempt.
 */
public class PatientReportParser {

    private static final Logger log = LoggerFactory.getLogger(PatientReportParser.class);

    public PatientReportData parse(byte[] pdfBytes, String sampleId) {
        byte[] chromatogramPng = null;
        String text = "";

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDPage page = doc.getPage(0);
            chromatogramPng = extractChromatogramPng(page);

            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(doc);
        } catch (Exception e) {
            log.error("Failed to parse patient report PDF for sample {}", sampleId, e);
        }

        PeakTableTextParser.Result parsed = PeakTableTextParser.parse(text);
        ReportHeader header = ReportHeaderTextParser.parse(text);

        return new PatientReportData(sampleId, parsed.totalArea, parsed.concentrationA1cPercent,
                parsed.peaks, chromatogramPng, header);
    }

    private byte[] extractChromatogramPng(PDPage page) throws Exception {
        PDResources resources = page.getResources();
        if (resources == null) {
            return null;
        }
        for (COSName name : resources.getXObjectNames()) {
            Object xobj = resources.getXObject(name);
            if (xobj instanceof PDImageXObject) {
                BufferedImage bImg = ((PDImageXObject) xobj).getImage();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImg, "PNG", baos);
                return baos.toByteArray();
            }
        }
        return null;
    }
}
