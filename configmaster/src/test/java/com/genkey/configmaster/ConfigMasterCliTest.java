package com.genkey.configmaster;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigMasterCliTest {

    @TempDir
    Path tempDir;

    @Test
    void noArgumentsReturnsUsageError() {
        int exitCode = ConfigMasterCli.run(new String[]{});

        assertEquals(2, exitCode);
    }

    @Test
    void validateReturnsZeroForValidConfig() throws Exception {
        Path spec = writeSpec();
        Path config = tempDir.resolve("valid.json");

        Files.writeString(config, """
                {
                  "app": {
                    "port": 8080
                  }
                }
                """);

        int exitCode = ConfigMasterCli.run(new String[]{
                "validate",
                "--spec", spec.toString(),
                config.toString()
        });

        assertEquals(0, exitCode);
    }

    @Test
    void validateReturnsOneForSchemaIssue() throws Exception {
        Path spec = writeSpec();
        Path config = tempDir.resolve("invalid.json");

        Files.writeString(config, """
                {
                  "app": {
                    "port": "8080"
                  }
                }
                """);

        int exitCode = ConfigMasterCli.run(new String[]{
                "validate",
                "--spec", spec.toString(),
                config.toString()
        });

        assertEquals(1, exitCode);
    }

    @Test
    void showReturnsZeroAndPrintsMergedJsonForValidConfig() throws Exception {
        Path spec = writeSpec();
        Path defaults = tempDir.resolve("defaults.json");
        Path overlay = tempDir.resolve("overlay.json");

        Files.writeString(defaults, """
                {
                  "app": {
                    "port": 8080,
                    "name": "configmaster"
                  }
                }
                """);

        Files.writeString(overlay, """
                {
                  "app": {
                    "port": 9090
                  }
                }
                """);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(capturedOut));

            int exitCode = ConfigMasterCli.run(new String[]{
                    "show",
                    "--spec", spec.toString(),
                    defaults.toString(),
                    overlay.toString()
            });

            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String output = capturedOut.toString();

        assertTrue(output.contains("\"port\" : 9090"));
        assertTrue(output.contains("\"name\" : \"configmaster\""));
    }

    @Test
    void malformedJsonReturnsUsageError() throws Exception {
        Path spec = writeSpec();
        Path config = tempDir.resolve("broken.json");

        Files.writeString(config, """
                {
                  "app": {
                    "port": 8080
                """);

        int exitCode = ConfigMasterCli.run(new String[]{
                "validate",
                "--spec", spec.toString(),
                config.toString()
        });

        assertEquals(2, exitCode);
    }

    @Test
    void unknownCommandReturnsUsageError() throws Exception {
        Path spec = writeSpec();
        Path config = tempDir.resolve("valid.json");

        Files.writeString(config, """
                {
                  "app": {
                    "port": 8080
                  }
                }
                """);

        int exitCode = ConfigMasterCli.run(new String[]{
                "unknown",
                "--spec", spec.toString(),
                config.toString()
        });

        assertEquals(2, exitCode);
    }

    private Path writeSpec() throws Exception {
        Path spec = tempDir.resolve("client-spec.json");

        Files.writeString(spec, """
                {
                  "schemaVersion": "1.0.0",
                  "client": "GenKey",
                  "requiredFields": [
                    "app.port"
                  ],
                  "fieldTypes": {
                    "app.port": "integer"
                  },
                  "mergeStrategies": {},
                  "defaultMergeStrategy": "override"
                }
                """);

        return spec;
    }
    
    @Test
    void rejectsUnsupportedSchemaVersion() throws Exception {
        Path specFile = tempDir.resolve("client-spec.json");
        Files.writeString(specFile, """
                {
                  "schemaVersion": "2.0.0",
                  "client": "Test Client",
                  "requiredFields": [],
                  "fieldTypes": {},
                  "mergeStrategies": {},
                  "defaultMergeStrategy": "override"
                }
                """);

        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{}");

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int exitCode;

        try {
            System.setOut(new PrintStream(output));
            System.setErr(new PrintStream(errors));

            exitCode = ConfigMasterCli.run(new String[] {
                    "validate",
                    "--spec", specFile.toString(),
                    configFile.toString()
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        assertEquals(2, exitCode);
        assertTrue(errors.toString().contains("unsupported schemaVersion: 2.0.0"));
        assertTrue(output.toString().isEmpty());
    }
}
