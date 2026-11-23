package com.zernex.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafArchiveBridgeTest {
    @Test fun archiveBaseNameHandlesCompoundExtensions() {
        assertEquals("backup", archiveBaseNameForSaf("backup.tar.gz"))
        assertEquals("backup", archiveBaseNameForSaf("backup.tar.bz2"))
        assertEquals("photos", archiveBaseNameForSaf("photos.zip"))
        assertEquals("Archive", archiveBaseNameForSaf(".xz"))
    }

    @Test fun archiveEntryNamesRejectTraversalAndSeparators() {
        assertTrue(isSafeArchiveLeafName("report.pdf"))
        assertTrue(isSafeArchiveLeafName("folder name"))
        assertFalse(isSafeArchiveLeafName("../outside.txt"))
        assertFalse(isSafeArchiveLeafName("folder/file.txt"))
        assertFalse(isSafeArchiveLeafName("folder\\file.txt"))
        assertFalse(isSafeArchiveLeafName("C:drive"))
        assertFalse(isSafeArchiveLeafName(".."))
    }
}
