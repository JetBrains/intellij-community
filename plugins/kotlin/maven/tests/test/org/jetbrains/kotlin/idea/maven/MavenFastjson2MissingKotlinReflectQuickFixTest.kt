// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.testFramework.enableInspectionTools
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.Fastjson2MissingKotlinReflectInspection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenFastjson2MissingKotlinReflectQuickFixTest(mavenVersion: String, modelVersion: String) :
    AbstractMavenUpdateConfigurationQuickFixTest(mavenVersion, modelVersion) {

    override val testRoot: String
        get() = "maven/tests/testData/fastjson2MissingKotlinReflect"

    @BeforeEach
    fun enableInspections() {
        enableInspectionTools(project, codeInsightTestFixture.testRootDisposable, Fastjson2MissingKotlinReflectInspection())
    }

    @Test
    fun testAddKotlinReflectFastjson2() = runBlocking {
        doTest("Add 'kotlin-reflect.jar' to the classpath")
    }

    @Test
    fun testNoWarningWhenKotlinReflectTransitive() = runBlocking {
        doTest("Add 'kotlin-reflect.jar' to the classpath", shouldBeAvailable = false)
    }
}
