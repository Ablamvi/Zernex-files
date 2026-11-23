package com.zernex.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileVisualsTest {
    @Test fun directoryIsFolder() {
        assertEquals(ZernexFileCategory.FOLDER, zernexCategoryFor("photos", true))
    }

    @Test fun mediaExtensionsShareRedCategory() {
        listOf("photo.jpg", "clip.mp4", "track.flac").forEach {
            assertEquals(it, ZernexFileCategory.MEDIA, zernexCategoryFor(it, false))
        }
    }

    @Test fun documentsShareGreenCategory() {
        listOf("report.pdf", "notes.txt", "table.xlsx", "source.kt").forEach {
            assertEquals(it, ZernexFileCategory.DOCUMENT, zernexCategoryFor(it, false))
        }
    }

    @Test fun archiveExtensionsShareYellowCategory() {
        listOf("backup.zip", "bundle.7z", "backup.tar.gz").forEach {
            assertEquals(it, ZernexFileCategory.ARCHIVE, zernexCategoryFor(it, false))
        }
    }

    @Test fun applicationPackagesShareYellowCategory() {
        listOf("app.apk", "split.apks", "bundle.xapk").forEach {
            assertEquals(it, ZernexFileCategory.APPLICATION, zernexCategoryFor(it, false))
        }
    }

    @Test fun unknownTypesStayNeutral() {
        assertEquals(ZernexFileCategory.OTHER, zernexCategoryFor("blob.unknown", false))
    }
}
