package com.cleaner.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMoverTest {
    @Test
    void moveInPutsTheFileInsideTheQuarantineFolderUnderAUniqueName(@TempDir Path tmp) throws Exception {
        Path src = Files.writeString(tmp.resolve("photo.jpg"), "data");
        Path q = tmp.resolve("quarantine");
        Path moved = FileMover.moveIn(src, q, "photo.jpg");
        assertFalse(Files.exists(src));
        assertEquals(q, moved.getParent());
        assertTrue(moved.getFileName().toString().endsWith("_photo.jpg"));
        assertEquals("data", Files.readString(moved));
    }

    @Test
    void sameNameTwiceNeverCollides(@TempDir Path tmp) throws Exception {
        Path q = tmp.resolve("q");
        Path a = FileMover.moveIn(Files.writeString(tmp.resolve("x"), "1"), q, "same.txt");
        Path b = FileMover.moveIn(Files.writeString(tmp.resolve("y"), "2"), q, "same.txt");
        assertFalse(a.equals(b));
        assertEquals("1", Files.readString(a));
        assertEquals("2", Files.readString(b));
    }

    @Test
    void hostileNamesCannotEscapeTheQuarantineFolder(@TempDir Path tmp) throws Exception {
        Path q = tmp.resolve("q");
        Path moved = FileMover.moveIn(Files.writeString(tmp.resolve("x"), "1"), q, "..\\..\\evil/../name.txt");
        assertEquals(q, moved.getParent());
    }

    @Test
    void restoreRecreatesMissingFolders(@TempDir Path tmp) throws Exception {
        Path quarantined = Files.writeString(tmp.resolve("held"), "data");
        Path original = tmp.resolve("gone/deep/file.txt");
        Path back = FileMover.moveBack(quarantined, original);
        assertEquals(original, back);
        assertEquals("data", Files.readString(back));
    }

    @Test
    void restoreNeverOverwritesAnExistingFile(@TempDir Path tmp) throws Exception {
        Path original = Files.writeString(tmp.resolve("photo.jpg"), "NEW");
        Path quarantined = Files.writeString(tmp.resolve("held"), "OLD");
        Path back = FileMover.moveBack(quarantined, original);
        assertEquals("photo (restored).jpg", back.getFileName().toString());
        assertEquals("NEW", Files.readString(original));
        assertEquals("OLD", Files.readString(back));

        Path again = FileMover.moveBack(Files.writeString(tmp.resolve("held2"), "OLD2"), original);
        assertEquals("photo (restored 2).jpg", again.getFileName().toString());
    }
}
