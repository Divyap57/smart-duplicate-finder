package com.cleaner.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashUtilTest {
    @Test
    void sha256MatchesKnownVectors(@TempDir Path tmp) throws Exception {
        Path hello = Files.writeString(tmp.resolve("hello.txt"), "hello world");
        Path empty = Files.write(tmp.resolve("empty"), new byte[0]);
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                HashUtil.hash(hello, "SHA-256", ScanControl.NONE));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.hash(empty, "SHA-256", ScanControl.NONE));
    }

    @Test
    void sha512HasLongerDigestAndIsDetectedFromLength(@TempDir Path tmp) throws Exception {
        Path f = Files.writeString(tmp.resolve("a"), "abc");
        String h = HashUtil.hash(f, "SHA-512", ScanControl.NONE);
        assertEquals(128, h.length());
        assertEquals("SHA-512", HashUtil.algorithmForHex(h));
        assertEquals("SHA-256", HashUtil.algorithmForHex(HashUtil.hash(f, "SHA-256", ScanControl.NONE)));
    }

    @Test
    void partialHashOnlyLooksAtTheStartOfTheFile(@TempDir Path tmp) throws Exception {
        byte[] a = new byte[200_000];
        new Random(7).nextBytes(a);
        byte[] b = a.clone();
        b[b.length - 1] ^= 1; // differs only at the very end
        Path fa = Files.write(tmp.resolve("a"), a);
        Path fb = Files.write(tmp.resolve("b"), b);
        assertEquals(HashUtil.partial(fa, "SHA-256", 65536), HashUtil.partial(fb, "SHA-256", 65536));
        assertNotEquals(HashUtil.hash(fa, "SHA-256", ScanControl.NONE), HashUtil.hash(fb, "SHA-256", ScanControl.NONE));
    }

    @Test
    void cancelDuringHashingIsPropagated(@TempDir Path tmp) throws Exception {
        Path f = Files.write(tmp.resolve("big"), new byte[3 * 1024 * 1024]);
        assertThrows(ScanCancelledException.class, () -> HashUtil.hash(f, "SHA-256", () -> {
            throw new ScanCancelledException();
        }));
    }

    @Test
    void unknownAlgorithmIsRejected(@TempDir Path tmp) throws Exception {
        Path f = Files.writeString(tmp.resolve("a"), "x");
        assertThrows(IllegalArgumentException.class, () -> HashUtil.hash(f, "NOPE-1", ScanControl.NONE));
    }
}
