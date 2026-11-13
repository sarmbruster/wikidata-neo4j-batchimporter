package de.thm.mni.wikidata.neo4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.logging.log4j.Log4jLogProvider;

/** Optional log file that receives our own messages, neo4j's internal log and neo4j's progress output. */
final class ImportLog implements AutoCloseable {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");

    private final PrintStream file;
    private final InternalLogProvider neo4jLogProvider;

    private ImportLog(PrintStream file, InternalLogProvider neo4jLogProvider) {
        this.file = file;
        this.neo4jLogProvider = neo4jLogProvider;
    }

    static ImportLog open(Path logFile) throws IOException {
        if (logFile == null) {
            return new ImportLog(null, NullLogProvider.getInstance());
        }
        Path parent = logFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var file = new PrintStream(Files.newOutputStream(logFile), true, StandardCharsets.UTF_8);
        return new ImportLog(file, new Log4jLogProvider(file));
    }

    void info(String message) {
        write("INFO ", message);
    }

    void error(String message, Throwable failure) {
        var stackTrace = new StringWriter();
        failure.printStackTrace(new PrintWriter(stackTrace));
        write("ERROR", message + System.lineSeparator() + stackTrace);
    }

    InternalLogProvider neo4jLogProvider() {
        return neo4jLogProvider;
    }

    /** Returns a stream that writes to the console (may be null) and to the log file. */
    PrintStream tee(PrintStream console) {
        if (file == null) {
            return console;
        }
        if (console == null) {
            return file;
        }
        return new PrintStream(new TeeOutputStream(console, file), true, StandardCharsets.UTF_8);
    }

    private void write(String level, String message) {
        if (file != null) {
            file.println(TIMESTAMP.format(ZonedDateTime.now(ZoneOffset.UTC)) + " " + level + " " + message);
        }
    }

    @Override
    public void close() throws IOException {
        neo4jLogProvider.close();
        if (file != null) {
            file.close();
        }
    }

    private static final class TeeOutputStream extends OutputStream {

        private final OutputStream first;
        private final OutputStream second;

        TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            first.write(bytes, offset, length);
            second.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
