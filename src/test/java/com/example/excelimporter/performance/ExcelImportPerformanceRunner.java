package com.example.excelimporter.performance;

import com.example.excelimporter.ExcelImporterApplication;
import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.service.ExcelImportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

public final class ExcelImportPerformanceRunner {

    private static final String[] FIRST_NAMES = {
            "Ada", "Grace", "Katherine", "Alan", "Edsger", "Barbara", "Donald", "Frances", "Ken", "Margaret"
    };
    private static final String[] LAST_NAMES = {
            "Lovelace", "Hopper", "Johnson", "Turing", "Dijkstra", "Liskov", "Knuth", "Allen", "Thompson", "Hamilton"
    };

    private ExcelImportPerformanceRunner() {
    }

    public static void main(String[] args) throws Exception {
        RunnerOptions options = RunnerOptions.parse(args);
        if (options.help) {
            printHelp();
            return;
        }

        createParentDirectory(options.output);
        if (!options.importOnly) {
            generateWorkbook(options);
        } else if (!Files.exists(options.output)) {
            throw new IllegalArgumentException("Input file does not exist: " + options.output);
        }

        if (!options.generateOnly) {
            importWorkbook(options);
        }
    }

    private static void generateWorkbook(RunnerOptions options) throws IOException {
        if (Files.exists(options.output) && !options.force) {
            System.out.println("Workbook already exists. Use --force=true to regenerate: " + options.output);
            return;
        }

        long startedAt = System.nanoTime();
        SXSSFWorkbook workbook = new SXSSFWorkbook(options.rowWindow);
        workbook.setCompressTempFiles(true);
        try {
            Sheet sheet = workbook.createSheet("customers");
            writeHeader(sheet);
            SplittableRandom random = new SplittableRandom(options.seed);

            for (int rowIndex = 1; rowIndex <= options.rows; rowIndex++) {
                writeCustomerRow(sheet.createRow(rowIndex), rowIndex, random, options);
                if (options.progressEvery > 0 && rowIndex % options.progressEvery == 0) {
                    System.out.printf(Locale.ENGLISH, "Generated %,d rows...%n", rowIndex);
                }
            }

            try (OutputStream outputStream = Files.newOutputStream(options.output)) {
                workbook.write(outputStream);
            }
        } finally {
            workbook.dispose();
            workbook.close();
        }

        long elapsedNanos = System.nanoTime() - startedAt;
        printGenerationMetrics(options, elapsedNanos);
    }

    private static void importWorkbook(RunnerOptions options) throws IOException {
        long memoryBefore = usedMemoryBytes();
        long startedAt = System.nanoTime();

        PropertySnapshot propertySnapshot = PropertySnapshot.apply(applicationProperties(options));
        ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(ExcelImporterApplication.class).run();
            if (options.cleanDatabase) {
                cleanDatabase(context.getBean(JdbcTemplate.class));
            }

            ExcelImportService excelImportService = context.getBean(ExcelImportService.class);
            ImportReport report;
            try (InputStream inputStream = Files.newInputStream(options.output)) {
                report = excelImportService.importFile("customers", inputStream, options.output.getFileName().toString());
            }

            long elapsedNanos = System.nanoTime() - startedAt;
            long memoryAfter = usedMemoryBytes();
            printImportMetrics(options, report, elapsedNanos, memoryBefore, memoryAfter);
        } finally {
            if (context != null) {
                shutdownH2Quietly(context, options);
                context.close();
            }
            propertySnapshot.restore();
        }
    }

    private static void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("businessKey");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("email");
        header.createCell(3).setCellValue("amount");
    }

    private static void writeCustomerRow(Row row, int rowIndex, SplittableRandom random, RunnerOptions options) {
        int keyNumber = rowIndex;
        if (options.duplicateEvery > 0 && rowIndex > 1 && rowIndex % options.duplicateEvery == 0) {
            keyNumber = rowIndex - 1;
        }

        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String businessKey = String.format(Locale.ENGLISH, "C-%09d", keyNumber);
        String email = isEvery(rowIndex, options.invalidEmailEvery)
                ? "invalid-email-" + rowIndex
                : "customer" + rowIndex + "@example.com";
        BigDecimal amount = isEvery(rowIndex, options.largeAmountEvery)
                ? new BigDecimal("25000.00").add(new BigDecimal(random.nextInt(100000)).movePointLeft(2))
                : new BigDecimal(random.nextInt(1000000)).movePointLeft(2);

        row.createCell(0).setCellValue(businessKey);
        row.createCell(1).setCellValue(firstName + " " + lastName);
        row.createCell(2).setCellValue(email);
        row.createCell(3).setCellValue(amount.toPlainString());
    }

    private static boolean isEvery(int rowIndex, int every) {
        return every > 0 && rowIndex % every == 0;
    }

    private static Map<String, Object> applicationProperties(RunnerOptions options) {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("server.port", "0");
        properties.put("spring.datasource.url", options.databaseUrl);
        properties.put("spring.datasource.username", "sa");
        properties.put("spring.datasource.password", "");
        properties.put("spring.h2.console.enabled", "false");
        properties.put("logging.level.root", "WARN");
        properties.put("spring.main.banner-mode", "off");
        properties.put("app.security.import-password", "perf-password");
        properties.put("app.import.in-memory-row-threshold", String.valueOf(options.inMemoryThreshold));
        properties.put("app.import.chunk-size", String.valueOf(options.chunkSize));
        properties.put("app.import.issue-preview-limit", String.valueOf(options.issuePreviewLimit));
        return properties;
    }

    private static void cleanDatabase(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("delete from customer_import_staging");
        jdbcTemplate.update("delete from import_issues");
        jdbcTemplate.update("delete from import_jobs");
        jdbcTemplate.update("delete from customer_records");
    }

    private static void shutdownH2Quietly(ConfigurableApplicationContext context, RunnerOptions options) {
        if (!options.databaseUrl.startsWith("jdbc:h2:")) {
            return;
        }
        try {
            context.getBean(JdbcTemplate.class).execute("SHUTDOWN");
        } catch (RuntimeException ignored) {
            // The application context shutdown will close the pool; this is only to stop H2 file threads promptly.
        }
    }

    private static void printGenerationMetrics(RunnerOptions options, long elapsedNanos) throws IOException {
        double seconds = nanosToSeconds(elapsedNanos);
        System.out.printf(Locale.ENGLISH,
                "Generated %,d rows in %.2fs (%,.0f rows/s). File: %s (%.2f MB)%n",
                options.rows,
                seconds,
                rate(options.rows, seconds),
                options.output,
                megabytes(Files.size(options.output)));
    }

    private static void printImportMetrics(RunnerOptions options,
                                           ImportReport report,
                                           long elapsedNanos,
                                           long memoryBefore,
                                           long memoryAfter) throws IOException {
        double seconds = nanosToSeconds(elapsedNanos);
        System.out.println();
        System.out.println("Import performance result");
        System.out.println("-------------------------");
        System.out.printf(Locale.ENGLISH, "File: %s (%.2f MB)%n", options.output, megabytes(Files.size(options.output)));
        System.out.printf(Locale.ENGLISH, "Rows read: %,d%n", report.getTotalRows());
        System.out.printf(Locale.ENGLISH, "Duration: %.2fs%n", seconds);
        System.out.printf(Locale.ENGLISH, "Throughput: %,.0f rows/s%n", rate(report.getTotalRows(), seconds));
        System.out.printf(Locale.ENGLISH, "Status: %s%n", report.getStatus());
        System.out.printf(Locale.ENGLISH, "Created: %,d, Updated: %,d, Skipped: %,d%n",
                report.getCreatedRows(), report.getUpdatedRows(), report.getSkippedRows());
        System.out.printf(Locale.ENGLISH, "Blocking errors: %,d, Warnings: %,d%n",
                report.getBlockingErrorCount(), report.getWarningCount());
        System.out.printf(Locale.ENGLISH, "Approx heap before/after: %.2f MB / %.2f MB%n",
                megabytes(memoryBefore), megabytes(memoryAfter));
        System.out.printf(Locale.ENGLISH, "Threshold: %,d, Chunk size: %,d%n",
                options.inMemoryThreshold, options.chunkSize);
    }

    private static double rate(long rows, double seconds) {
        return seconds <= 0.0 ? 0.0 : rows / seconds;
    }

    private static double nanosToSeconds(long nanos) {
        return nanos / 1_000_000_000.0;
    }

    private static double megabytes(long bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    private static long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void createParentDirectory(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void printHelp() {
        System.out.println("Excel import performance runner");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  mvn -Pperformance -DskipTests test-compile exec:java -Dexec.args=\"--rows=100000 --force=true\"");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --rows=100000                  Number of data rows to generate.");
        System.out.println("  --output=target/performance/customers.xlsx");
        System.out.println("  --force=true                   Regenerate output file if it already exists.");
        System.out.println("  --generateOnly=true            Generate workbook without importing.");
        System.out.println("  --importOnly=true              Import an existing workbook.");
        System.out.println("  --invalidEmailEvery=0          Add one invalid email every N rows. 0 disables.");
        System.out.println("  --duplicateEvery=0             Duplicate the previous business key every N rows. 0 disables.");
        System.out.println("  --largeAmountEvery=0           Add warning-level amounts every N rows. 0 disables.");
        System.out.println("  --threshold=10000              In-memory row threshold before staging.");
        System.out.println("  --chunkSize=1000               JDBC staging chunk size.");
        System.out.println("  --cleanDatabase=true           Delete test data before import.");
    }

    private static final class RunnerOptions {

        private int rows = 100_000;
        private Path output;
        private boolean force;
        private boolean generateOnly;
        private boolean importOnly;
        private boolean cleanDatabase = true;
        private boolean help;
        private long seed = 42L;
        private int rowWindow = 500;
        private int progressEvery = 100_000;
        private int invalidEmailEvery;
        private int duplicateEvery;
        private int largeAmountEvery;
        private int inMemoryThreshold = 10_000;
        private int chunkSize = 1_000;
        private int issuePreviewLimit = 500;
        private String databaseUrl = "jdbc:h2:file:./target/performance/excel-importer;"
                + "MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";

        private static RunnerOptions parse(String[] args) {
            RunnerOptions options = new RunnerOptions();
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    options.help = true;
                    continue;
                }

                ParsedArgument parsedArgument = ParsedArgument.parse(arg);
                if (parsedArgument == null) {
                    continue;
                }
                options.apply(parsedArgument.name, parsedArgument.value);
            }

            if (options.output == null) {
                options.output = Paths.get("target", "performance", "customers-" + options.rows + ".xlsx");
            }
            return options;
        }

        private void apply(String name, String value) {
            if ("rows".equals(name)) {
                rows = positiveInt(name, value);
            } else if ("output".equals(name)) {
                output = Paths.get(value);
            } else if ("force".equals(name)) {
                force = Boolean.parseBoolean(value);
            } else if ("generateOnly".equals(name)) {
                generateOnly = Boolean.parseBoolean(value);
            } else if ("importOnly".equals(name)) {
                importOnly = Boolean.parseBoolean(value);
            } else if ("cleanDatabase".equals(name)) {
                cleanDatabase = Boolean.parseBoolean(value);
            } else if ("seed".equals(name)) {
                seed = Long.parseLong(value);
            } else if ("rowWindow".equals(name)) {
                rowWindow = positiveInt(name, value);
            } else if ("progressEvery".equals(name)) {
                progressEvery = nonNegativeInt(name, value);
            } else if ("invalidEmailEvery".equals(name)) {
                invalidEmailEvery = nonNegativeInt(name, value);
            } else if ("duplicateEvery".equals(name)) {
                duplicateEvery = nonNegativeInt(name, value);
            } else if ("largeAmountEvery".equals(name)) {
                largeAmountEvery = nonNegativeInt(name, value);
            } else if ("threshold".equals(name)) {
                inMemoryThreshold = nonNegativeInt(name, value);
            } else if ("chunkSize".equals(name)) {
                chunkSize = positiveInt(name, value);
            } else if ("issuePreviewLimit".equals(name)) {
                issuePreviewLimit = positiveInt(name, value);
            } else if ("databaseUrl".equals(name)) {
                databaseUrl = value;
            } else {
                throw new IllegalArgumentException("Unknown option: --" + name);
            }
        }

        private int positiveInt(String name, String value) {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("--" + name + " must be greater than 0.");
            }
            return parsed;
        }

        private int nonNegativeInt(String name, String value) {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("--" + name + " must be greater than or equal to 0.");
            }
            return parsed;
        }
    }

    private static final class ParsedArgument {

        private final String name;
        private final String value;

        private ParsedArgument(String name, String value) {
            this.name = name;
            this.value = value;
        }

        private static ParsedArgument parse(String arg) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Arguments must use --name=value format: " + arg);
            }
            int separator = arg.indexOf('=');
            if (separator < 0) {
                return new ParsedArgument(arg.substring(2), "true");
            }
            return new ParsedArgument(arg.substring(2, separator), arg.substring(separator + 1));
        }
    }

    private static final class PropertySnapshot {

        private final Map<String, String> previousValues;

        private PropertySnapshot(Map<String, String> previousValues) {
            this.previousValues = previousValues;
        }

        private static PropertySnapshot apply(Map<String, Object> properties) {
            Map<String, String> previousValues = new LinkedHashMap<String, String>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                previousValues.put(entry.getKey(), System.getProperty(entry.getKey()));
                System.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return new PropertySnapshot(previousValues);
        }

        private void restore() {
            for (Map.Entry<String, String> entry : previousValues.entrySet()) {
                if (entry.getValue() == null) {
                    System.clearProperty(entry.getKey());
                } else {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
