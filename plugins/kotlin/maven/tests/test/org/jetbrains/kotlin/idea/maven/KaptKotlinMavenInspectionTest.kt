// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.configuration.inspections.KaptKotlinCompilerPluginInspection
import org.junit.Test

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

    @Test
    fun testNoKaptCompilerPluginInspectionWhenKspConfigured() = runBlocking {
        doMultiFileTest {
            withContext(Dispatchers.EDT) {
                writeIntentReadAction {
                    assertTrue(
                        codeInsightTestFixture.filterAvailableIntentions("Add Kotlin kapt compiler plugin").isEmpty()
                    )
                }
            }
        }
    }
}
