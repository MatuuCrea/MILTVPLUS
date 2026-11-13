package com.matuucrea.miltvplus.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesPatchVersionsNumerically() {
        assertTrue(compareReleaseVersions("22.34.10", "22.34.9") > 0)
    }

    @Test fun treatsMissingTrailingComponentsAsZero() {
        assertEquals(0, compareReleaseVersions("22.34", "22.34.0"))
    }

    @Test fun acceptsReleaseTagPrefix() {
        assertTrue(compareReleaseVersions("v22.35.0", "22.34.99") > 0)
    }

    @Test fun recognizesOlderVersion() {
        assertTrue(compareReleaseVersions("22.34.8", "22.34.9") < 0)
    }
}
