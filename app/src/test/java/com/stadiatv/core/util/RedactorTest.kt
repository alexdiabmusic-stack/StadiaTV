package com.stadiatv.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    @Test
    fun redactsCredentialQueryParametersAndXtreamPaths() {
        val value = Redactor.url("https://host/live/demoUser/demoPass/123.ts?username=demoUser&password=demoPass&token=demoToken")

        assertTrue(value.contains("username=REDACTED"))
        assertTrue(value.contains("password=REDACTED"))
        assertTrue(value.contains("token=REDACTED"))
        assertFalse(value.contains("demoPass"))
        assertFalse(value.contains("demoUser/demoPass"))
    }
}
