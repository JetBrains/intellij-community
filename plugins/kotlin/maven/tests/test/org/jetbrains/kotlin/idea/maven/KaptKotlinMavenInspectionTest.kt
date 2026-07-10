// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import org.jetbrains.kotlin.idea.configuration.inspections.KaptKotlinCompilerPluginInspection
import org.junit.Test
import kotlinx.coroutines.runBlocking

internal class KaptKotlinMavenInspectionTest : AbstractMavenUpdateConfigurationQuickFixTest() {

    override val testRoot: String
        get() = "maven/tests/testData/kapt/fixes/"

    override fun setUpFixtures() {
        super.setUpFixtures()
        codeInsightTestFixture.enableInspections(KaptKotlinCompilerPluginInspection::class.java)
    }

    @Test
    fun testAddKaptCompilerPluginForMapstructProcessorDependency() = runBlocking {
        doMultiFileTest()
    }
}
