// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.google.gson.JsonParser
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KotlinMavenArchetypesProviderTest {
    companion object {
        private val BASE_PATH = IDEA_TEST_DATA_DIR.resolve("configuration")
    }

    @Test
    fun extractVersions() {
        val file = File(BASE_PATH, "extractVersions/maven-central-response.json")
        assertTrue("Test data is missing", file.exists())

        val json = file.bufferedReader().use {
            JsonParser.parseReader(it)
        }

        val versions = KotlinMavenArchetypesProvider("1.0.0-Release-Something-1886", false).extractVersions(json)

        assertEquals(
            listOf(
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.0.1-2", null, null),
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-js", "1.0.0", null, null)
            ).sortedBy { it.artifactId + "." + it.version },
            versions.sortedBy { it.artifactId + "." + it.version }
        )
    }

    @Test
    fun extractVersionsNewPlugin() {
        val file = File(BASE_PATH, "extractVersions/maven-central-response.json")
        assertTrue("Test data is missing", file.exists())

        val json = file.bufferedReader().use {
            JsonParser.parseReader(it)
        }

        val versions = KotlinMavenArchetypesProvider("1.1.0-Next-Release-Something-9999", false).extractVersions(json)

        assertEquals(
            listOf(
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.1.2", null, null)
            ).sortedBy { it.artifactId + "." + it.version },
            versions.sortedBy { it.artifactId + "." + it.version }
        )
    }

    @Test
    fun extractVersionsInternalMode() {
        val file = File(BASE_PATH, "extractVersions/maven-central-response.json")
        assertTrue("Test data is missing", file.exists())

        val json = file.bufferedReader().use {
            JsonParser.parseReader(it)
        }

        val versions = KotlinMavenArchetypesProvider("1.0.0-Release-Something-1886", true).extractVersions(json)

        assertEquals(
            listOf(
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.0.1-2", null, null),
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.1.2", null, null),
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-js", "1.0.0", null, null)
            ).sortedBy { it.artifactId + "." + it.version },
            versions.sortedBy { it.artifactId + "." + it.version }
        )
    }

    @Test
    fun extractVersionsTooDifferentPluginVersion() {
        val file = File(BASE_PATH, "extractVersions/maven-central-response.json")
        assertTrue("Test data is missing", file.exists())

        val json = file.bufferedReader().use {
            JsonParser.parseReader(it)
        }

        val versions = KotlinMavenArchetypesProvider("1.9.0-Missing-Release-Something-1886", false).extractVersions(json)

        assertEquals(
            listOf(
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.0.1-2", null, null),
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.1.2", null, null),
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-js", "1.0.0", null, null)
            ).sortedBy { it.artifactId + "." + it.version },
            versions.sortedBy { it.artifactId + "." + it.version }
        )
    }

    @Test
    fun extractVersionsSnapshotPlugin() {
        val file = File(BASE_PATH, "extractVersions/maven-central-response.json")
        assertTrue("Test data is missing", file.exists())

        val json = file.bufferedReader().use {
            JsonParser.parseReader(it)
        }

        val versions = KotlinMavenArchetypesProvider("@snapshot@", false).extractVersions(json)

        assertEquals(
            listOf(
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.0.1-2", null, null),
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-jvm", "1.1.2", null, null),
                MavenArchetype("org.jetbrains.kotlin", "kotlin-archetype-js", "1.0.0", null, null)
            ).sortedBy { it.artifactId + "." + it.version },
            versions.sortedBy { it.artifactId + "." + it.version }
        )
    }
}