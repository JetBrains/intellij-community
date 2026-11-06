// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.openapi.project.modules
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.maven.AbstractMavenImportingTest
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.TestFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class KaptMavenProjectConfiguratorTest(mavenVersion: String, modelVersion: String) :
    AbstractMavenImportingTest(mavenVersion, modelVersion) {

    override val testRoot: String
        get() = "maven/tests/testData/kapt/configurator/"

    @Test
    fun testConfigureKotlinAddsKaptToChildModuleWithProcessorVersion() = runBlocking {
        doMultiFileTest()
    }

    @Test
    fun testConfigureKotlinAddsKaptToChildModuleWithManagedProcessorVersion() = runBlocking {
        doMultiFileTest()
    }

    override fun doTestAction(mainFile: TestFile) {
        KotlinJavaMavenConfigurator().doInternalConfigure(
            project,
            IdeKotlinVersion.get(KOTLIN_VERSION),
            project.modules.toList(),
            NotificationMessageCollector.create(project),
            KotlinJavaMavenConfigurator().notificationHolder(project),
            emptyMap(),
            emptyMap(),
        ).build()
    }

    companion object {
        const val KOTLIN_VERSION = "2.4.0"
    }
}
