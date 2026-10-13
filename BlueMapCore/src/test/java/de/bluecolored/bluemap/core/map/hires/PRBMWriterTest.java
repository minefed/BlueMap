/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.core.map.hires;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PRBMWriterTest {
    @Test
    void emptyAndEdgeCaseModelsMatchOriginalBinaryGoldenBeforeClose() throws Exception {
        for (int triangles : new int[]{0, 8}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PRBMWriter writer = new PRBMWriter(bytes)) {
                writer.write(PRBMTestModels.fixture(triangles));
                assertArrayEquals(golden(triangles), bytes.toByteArray(), "write must not defer bytes until close");
            }
            assertArrayEquals(golden(triangles), bytes.toByteArray());
        }
    }

    @Test
    void largeModelMatchesOriginalWriterDigestAcrossManyBackendBufferBoundaries() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PRBMWriter writer = new PRBMWriter(new java.io.BufferedOutputStream(bytes, 127))) {
            writer.write(PRBMTestModels.fixture(8193));
        }
        assertEquals(774320, bytes.size());
        assertEquals("e226961a1b466c8089d5f0607fbb4a763123008c036b2c3fa9d44c0ad494b482",
                hex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
    }

    @Test
    void gzipAndRepeatedWritesKeepTheSameBytesAndAlignment() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PRBMWriter writer = new PRBMWriter(new GZIPOutputStream(bytes))) {
            writer.write(PRBMTestModels.fixture(8));
            writer.write(PRBMTestModels.fixture(0));
        }
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(golden(8));
        expected.write(golden(0));
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertArrayEquals(expected.toByteArray(), input.readAllBytes());
        }
    }

    @Test
    void eachFourByteScalarUsesOneBackendCallAndReusesItsArray() throws Exception {
        CountingSink sink = new CountingSink();
        try (PRBMWriter writer = new PRBMWriter(sink)) {
            writer.write(PRBMTestModels.fixture(8));
            // 15 position/UV floats per triangle, 6 material groups of 3 ints, then -1.
            assertEquals(139, sink.scalarCalls);
            assertEquals(243, sink.singleCalls);
            assertEquals(146, sink.bulkCalls);
            assertEquals(0, sink.closeCalls);
            assertEquals(0, sink.flushCalls);
        }
        assertEquals(1, sink.closeCalls);
        assertEquals(0, sink.flushCalls);
        assertArrayEquals(golden(8), sink.bytes.toByteArray());
    }

    @Test
    void writeFailuresPropagateImmediatelyWithoutRetryOrImplicitClose() throws Exception {
        byte[] expected = golden(8);
        for (int failAt : new int[]{0, 8, 20, 22, 23, 24, 71, 839}) {
            FailingSink sink = new FailingSink(failAt, false);
            PRBMWriter writer = new PRBMWriter(sink);
            assertSame(sink.writeError, assertThrows(IOException.class, () -> writer.write(PRBMTestModels.fixture(8))));
            assertArrayEquals(Arrays.copyOf(expected, failAt), sink.bytes.toByteArray());
            assertEquals(0, sink.closeCalls);
            writer.close();
            assertEquals(1, sink.closeCalls);
        }
    }

    @Test
    void closeFailuresPropagateAndRemainSuppressedBehindWriteFailures() throws Exception {
        FailingSink closeOnly = new FailingSink(Integer.MAX_VALUE, true);
        PRBMWriter writer = new PRBMWriter(closeOnly);
        writer.write(PRBMTestModels.fixture(0));
        assertSame(closeOnly.closeError, assertThrows(IOException.class, writer::close));
        assertEquals(1, closeOnly.closeCalls);

        FailingSink both = new FailingSink(22, true);
        IOException error = assertThrows(IOException.class, () -> {
            try (PRBMWriter failing = new PRBMWriter(both)) {
                failing.write(PRBMTestModels.fixture(8));
            }
        });
        assertSame(both.writeError, error);
        assertArrayEquals(new Throwable[]{both.closeError}, error.getSuppressed());
        assertEquals(1, both.closeCalls);
    }

    private static String hex(byte[] bytes) {
        return IntStream.range(0, bytes.length).mapToObj(i -> String.format("%02x", bytes[i] & 0xff)).collect(Collectors.joining());
    }

    private static byte[] golden(int triangles) throws IOException {
        try (var input = PRBMWriterTest.class.getResourceAsStream("/prbm/triangles-" + triangles + ".prbm")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private static final class CountingSink extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int singleCalls, bulkCalls, scalarCalls, closeCalls, flushCalls;
        byte[] scalarArray;
        public void write(int value) { singleCalls++; bytes.write(value); }
        public void write(byte[] data, int offset, int length) {
            bulkCalls++;
            if (length == Integer.BYTES) {
                scalarCalls++;
                if (scalarArray == null) scalarArray = data;
                else assertSame(scalarArray, data, "The writer must not allocate an array per scalar");
            }
            bytes.write(data, offset, length);
        }
        public void flush() { flushCalls++; }
        public void close() { closeCalls++; }
    }

    private static final class FailingSink extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final IOException writeError = new IOException("write fixture"), closeError = new IOException("close fixture");
        final int failAt;
        final boolean failClose;
        int closeCalls;
        FailingSink(int failAt, boolean failClose) { this.failAt = failAt; this.failClose = failClose; }
        public void write(int value) throws IOException {
            if (bytes.size() == failAt) throw writeError;
            bytes.write(value);
        }
        // Deliberately uses OutputStream's default bulk implementation, including partial failures.
        public void close() throws IOException {
            closeCalls++;
            if (failClose) throw closeError;
        }
    }
}
