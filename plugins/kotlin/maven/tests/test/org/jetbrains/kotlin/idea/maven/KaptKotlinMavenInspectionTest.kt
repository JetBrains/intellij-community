// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.configuration.inspections.KaptKotlinCompilerPluginInspection
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
internal class KaptKotlinMavenInspectionTest(mavenVersion: String, modelVersion: String) :
    AbstractMavenUpdateConfigurationQuickFixTest(mavenVersion, modelVersion) {

    override val testRoot: String
        get() = "maven/tests/testData/kapt/fixes/"

    @BeforeEach
    fun enableInspections() {
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
