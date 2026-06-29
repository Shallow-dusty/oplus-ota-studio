package dev.shallowdusty.oplusotastudio.core.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResumeRequestPlannerTest {

    private val planner = ResumeRequestPlanner()

    @Test
    fun `resumes with range when validators match and ranges are supported`() {
        val plan = planner.plan(
            stored = ResumeSnapshot(
                acceptRanges = true,
                downloadedBytes = 1024L,
                partFileBytes = 1024L,
                etag = "\"abc\"",
                lastModified = "Tue, 30 Jun 2026 00:00:00 GMT",
            ),
            current = ResumeValidators(
                etag = "\"abc\"",
                lastModified = "Tue, 30 Jun 2026 00:00:00 GMT",
            ),
        )

        assertEquals(1024L, plan.rangeStart)
        assertFalse(plan.discardPartial)
        assertEquals(null, plan.truncateToBytes)
    }

    @Test
    fun `truncates oversized part file before resuming`() {
        val plan = planner.plan(
            stored = ResumeSnapshot(
                acceptRanges = true,
                downloadedBytes = 1024L,
                partFileBytes = 2048L,
                etag = "\"abc\"",
                lastModified = null,
            ),
            current = ResumeValidators(
                etag = "\"abc\"",
                lastModified = null,
            ),
        )

        assertEquals(1024L, plan.rangeStart)
        assertEquals(1024L, plan.truncateToBytes)
        assertFalse(plan.discardPartial)
    }

    @Test
    fun `restarts when validators changed`() {
        val plan = planner.plan(
            stored = ResumeSnapshot(
                acceptRanges = true,
                downloadedBytes = 1024L,
                partFileBytes = 1024L,
                etag = "\"old\"",
                lastModified = null,
            ),
            current = ResumeValidators(
                etag = "\"new\"",
                lastModified = null,
            ),
        )

        assertEquals(0L, plan.rangeStart)
        assertTrue(plan.discardPartial)
        assertEquals("Remote package changed, restarting from 0.", plan.reason)
    }

    @Test
    fun `restarts when server does not support ranges`() {
        val plan = planner.plan(
            stored = ResumeSnapshot(
                acceptRanges = false,
                downloadedBytes = 1024L,
                partFileBytes = 1024L,
                etag = "\"abc\"",
                lastModified = null,
            ),
            current = ResumeValidators(
                etag = "\"abc\"",
                lastModified = null,
            ),
        )

        assertEquals(0L, plan.rangeStart)
        assertTrue(plan.discardPartial)
        assertEquals("Server does not support range resume.", plan.reason)
    }
}
