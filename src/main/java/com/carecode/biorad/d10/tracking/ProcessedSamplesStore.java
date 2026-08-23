package com.carecode.biorad.d10.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which sample IDs have already been sent to the LIS for a given day,
 * so re-polling the analyzer never sends the same result twice. One plain
 * text file per day, e.g. processed_samples_2026-08-23.txt.
 */
public class ProcessedSamplesStore {

    private static final Logger log = LoggerFactory.getLogger(ProcessedSamplesStore.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path directory;

    public ProcessedSamplesStore(String directory) {
        this.directory = Paths.get(directory == null || directory.isEmpty() ? "state" : directory);
    }

    private Path fileFor(LocalDate date) {
        return directory.resolve("processed_samples_" + date.format(FILE_DATE_FORMAT) + ".txt");
    }

    public Set<String> loadProcessed(LocalDate date) {
        Set<String> processed = new HashSet<>();
        Path path = fileFor(date);
        if (Files.exists(path)) {
            try {
                processed.addAll(Files.readAllLines(path));
            } catch (IOException e) {
                log.error("Error reading processed samples file: {}", path, e);
            }
        }
        return processed;
    }

    public void markProcessed(LocalDate date, String sampleId) {
        try {
            Files.createDirectories(directory);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileFor(date).toFile(), true))) {
                writer.write(sampleId);
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Error writing processed samples file for sample {}", sampleId, e);
        }
    }
}
