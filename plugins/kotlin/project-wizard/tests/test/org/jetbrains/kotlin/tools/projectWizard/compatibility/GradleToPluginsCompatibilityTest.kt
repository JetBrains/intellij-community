// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.gradle.util.GradleVersion

class GradleToPluginsCompatibilityTest  : BasePlatformTestCase() {

    fun testDefaultData() {
        val state = GradleToPluginsCompatibilityStore.getInstance().state
        assertNotNull("Gradle to plugins compatibility matrix should exist", state)
        state ?: return
        for (plugin in state.plugins) {
            assertFalse("Gradle to plugins compatibility should not be empty", plugin.compatibility.isEmpty())
        }
    }

    fun testGradleFoojayCompatibility() {
        val store = GradleToPluginsCompatibilityStore.getInstance()

        val gradle7_5 = GradleVersion.version("7.5")
        val foojayVersionForGradle7_5 = store.getFoojayVersion(gradle7_5)
        assertTrue("Foojay is not supported for Gradle less than 7.6", foojayVersionForGradle7_5 == null)

        val gradle7_6 = GradleVersion.version("7.6")
        val foojayVersionForGradle7_6 = store.getFoojayVersion(gradle7_6)
        assertTrue("Foojay version for Gradle 7.6 is 0.10.0", foojayVersionForGradle7_6 == "0.10.0")

        val gradle9_0 = GradleVersion.version("9.0")
        val foojayVersionForGradle9_0 = store.getFoojayVersion(gradle9_0)
        assertTrue("Foojay version for Gradle 9.0 is 1.0.0", foojayVersionForGradle9_0 == "1.0.0")
    }
}