package com.gohj99.wearnfc.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshNfcWaitTest {
    @Test fun waitRangeAndSliderSupportEveryMillisecond() {
        assertEquals(200, DEFAULT_REFRESH_NFC_WAIT_MILLIS)
        assertEquals(1, Config.MIN_REFRESH_NFC_WAIT_MILLIS)
        assertEquals(500, Config.MAX_REFRESH_NFC_WAIT_MILLIS)
        assertEquals(1, Config.REFRESH_NFC_WAIT_STEP_MILLIS)
        assertEquals(500, normalizeRefreshNfcWaitMillis(500))
    }

    @Test fun savedAndSubmittedValuesAreClampedToTheNewRange() {
        assertEquals(1, normalizeRefreshNfcWaitMillis(Int.MIN_VALUE))
        assertEquals(1, normalizeRefreshNfcWaitMillis(1))
        assertEquals(500, normalizeRefreshNfcWaitMillis(500))
        assertEquals(500, normalizeRefreshNfcWaitMillis(Int.MAX_VALUE))
        // Existing settings above 500 ms are clamped on the next load.
        assertEquals(500, normalizeRefreshNfcWaitMillis(1_000))
    }
}
