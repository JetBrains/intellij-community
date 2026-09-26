// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenUpdateConfigurationQuickFixTest12(mavenVersion: String, modelVersion: String) :
    AbstractMavenUpdateConfigurationQuickFixTest(mavenVersion, modelVersion) {

    override val testRoot: String
        get() = "maven/tests/testData/languageFeature"

    @Test
    fun testUpdateLanguageVersion() = runBlocking {
        doTest("Increase language version to 2.2")
    }

    @Test
    fun testUpdateLanguageVersionProperty() = runBlocking {
        doTest("Increase language version to 2.2")
    }

    @Test
    fun testUpdateLanguageAndApiVersion() = runBlocking {
        doTest("Increase language version to 2.2")
    }

    @Test
    fun testAddKotlinReflect() = runBlocking {
        doTest("Add 'kotlin-reflect.jar' to the classpath")
    }
}
