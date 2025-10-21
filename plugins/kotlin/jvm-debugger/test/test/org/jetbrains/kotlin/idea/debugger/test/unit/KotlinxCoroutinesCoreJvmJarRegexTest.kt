// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.unit

import org.jetbrains.kotlin.idea.debugger.coroutine.CoroutineAgentConnector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class KotlinxCoroutinesCoreJvmJarRegexTest {
    private val regex = CoroutineAgentConnector.KOTLINX_COROUTINES_CORE_JVM_JAR_REGEX

    @Test
    fun matchesCoreWithoutJvmSuffix() {
        val path = "/repo/.m2/kotlinx-coroutines-core-1.7.3.jar"
        val match = regex.matchEntire(path)

        assertNotNull(match, "Expected to match path without -jvm suffix")
        assertEquals("1.7.3", match!!.groupValues[2])
    }

    @Test
    fun matchesCoreWithJvmSuffix() {
        val path = "/repo/.m2/kotlinx-coroutines-core-jvm-1.8.0.jar"
        val match = regex.matchEntire(path)

        assertNotNull(match, "Expected to match path with -jvm suffix")
        assertEquals("1.8.0", match!!.groupValues[2])
    }

    @Test
    fun matchesComplexVersion() {
        val path = "/repo/.m2/kotlinx-coroutines-core-1.3.7-255.jar"
        val match = regex.matchEntire(path)

        assertNotNull(match, "Expected to match complex version like 1.3.7-255")
        assertEquals("1.3.7-255", match!!.groupValues[2])
    }

    @Test
    fun doesNotMatchOtherArtifacts() {
        assertNull(regex.matchEntire("/repo/.m2/kotlin-stdlib-1.9.24.jar"), "Should not match other artifacts like kotlin-stdlib")
        assertNull(regex.matchEntire("/repo/.m2/kotlinx-coroutines-core-common-1.7.3.jar"), "Should not match core-common variant")
    }

    @Test
    fun doesNotMatchMalformedJarFiles() {
        // Missing required hyphen before version
        assertNull(regex.matchEntire("/repo/.m2/kotlinx-coroutines-core-jvm.jar"), "Should not match without version")
        // Wrong separator between artifact and version
        assertNull(regex.matchEntire("/repo/.m2/kotlinx-coroutines-core.1.7.3.jar"), "Should not match with '.' before version")
    }

    @Test
    fun matchesBazelRulesJvmExternalJar() {
        val path =
            "/bin/external/rules_jvm_external++maven+maven/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/processed_kotlinx-coroutines-core-jvm-1.9.0.jar"
        val match = regex.matchEntire(path)

        assertNotNull(match)
        assertEquals("1.9.0", match!!.groupValues[2])
    }
}