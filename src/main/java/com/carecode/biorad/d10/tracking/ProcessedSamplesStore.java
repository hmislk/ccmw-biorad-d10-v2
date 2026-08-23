package com.carecode.biorad.d10.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks every sample ID that has ever been sent to the LIS, so a sample is
 * never sent twice -- regardless of which polling cycle or which date
 * bucket (today/yesterday) it turns up in. Backed by a single flat file
 * (one sample ID per line); a lab easily runs a few thousand samples a
 * year, so this file stays tiny indefinitely.
 */
public class ProcessedSamplesStore {

    private static final Logger log = LoggerFactory.getLogger(ProcessedSamplesStore.class);

    private final Path registryFile;

    public ProcessedSamplesStore(String directory) {
        Path dir = Paths.get(directory == null || directory.isEmpty() ? "state" : directory);
        this.registryFile = dir.resolve("sent_samples.txt");
    }

    public Set<String> loadSentSampleIds() {
        Set<String> sent = new HashSet<>();
        if (Files.exists(registryFile)) {
            try {
                sent.addAll(Files.readAllLines(registryFile));
            } catch (IOException e) {
                log.error("Error reading sent-samples registry: {}", registryFile, e);
            }
        }
        return sent;
    }

    public void markSent(String sampleId) {
        try {
            Files.createDirectories(registryFile.getParent());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(registryFile.toFile(), true))) {
                writer.write(sampleId);
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Error writing sent-samples registry for sample {}", sampleId, e);
        }
    }
}
