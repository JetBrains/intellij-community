// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix

class GradleCompatibilityTest : BasePlatformTestCase() {
    fun testDefaultData() {
        val state = KotlinGradleCompatibilityStore.getInstance().state
        assertNotNull("Kotlin Gradle compatibility matrix should exist", state)
        state ?: return
        assertFalse("Kotlin Gradle compatibility should not be empty", state.compatibility.isEmpty())
    }

    fun testKotlinVersionGradleCompatibility() {
        val kotlinVersion = KotlinWizardVersionStore.getInstance().state?.kotlinPluginVersion ?: return
        val ideKotlinVersion = IdeKotlinVersion.get(kotlinVersion)

        val compatibility = KotlinGradleCompatibilityStore.allKotlinVersions()
        assertTrue(
            "Kotlin/Gradle compatibility matrix should contain current Kotlin version",
            compatibility.contains(ideKotlinVersion)
        )
        val allGradleVersions = GradleJvmSupportMatrix.getAllSupportedGradleVersionsByIdea()

        assertTrue(
            "Kotlin/Gradle compatibility matrix should support current Kotlin version",
            allGradleVersions.any {
                KotlinGradleCompatibilityStore.kotlinVersionSupportsGradle(ideKotlinVersion, it)
            }
        )
    }
}