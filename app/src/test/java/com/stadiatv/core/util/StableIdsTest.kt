package com.stadiatv.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableIdsTest {
    @Test
    fun m3uIdsDoNotDependOnListPosition() {
        val first = StableIds.m3uMediaId("source-a", "bbc.one", "BBC One", "UK", "https://cdn.example/live/bbc.ts")
        val second = StableIds.m3uMediaId("source-a", "bbc.one", "BBC One HD", "News", "https://cdn.example/live/bbc.ts")

        assertEquals(first, second)
    }

    @Test
    fun fingerprintsChangeWhenUrlChangesWithoutEpgId() {
        val first = StableIds.m3uMediaId("source-a", null, "BBC One", "UK", "https://cdn.example/live/bbc.ts")
        val second = StableIds.m3uMediaId("source-a", null, "BBC One", "UK", "https://cdn.example/live/bbc2.ts")

        assertNotEquals(first, second)
    }
}
