// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.notification.asText
import org.jetbrains.kotlin.idea.notification.catchNotificationTextAsync
import org.jetbrains.kotlin.idea.notification.catchNotificationsAsync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JpsCompilerTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJpsCompilerUnsupportedVersionDown() = runBlocking {
        val version = "1.1.0"
        val notifications = catchNotificationsAsync(project, maven.testRootDisposable) {
            doUnsupportedVersionTest(version, KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler)
        }

        val notification = notifications.find { it.title == "Unsupported Kotlin JPS plugin version" }
        assertNotNull(notifications.asText(), notification)
        assertEquals(
            notification?.content,
            "Version (${KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler}) of the Kotlin JPS plugin will be used<br>" +
                    "The reason: Kotlin JPS compiler minimum supported version is '${KotlinJpsPluginSettings.jpsMinimumSupportedVersion}' but '$version' is specified",
        )
    }

    @Test
    fun testJpsCompilerUnsupportedVersionUp() = runBlocking {
        val maxVersion = KotlinJpsPluginSettings.jpsMaximumSupportedVersion
        val versionToImport = KotlinVersion(maxVersion.major, maxVersion.minor, maxVersion.minor + 1)
        val text = catchNotificationTextAsync(project, "Kotlin JPS plugin", maven.testRootDisposable) {
            doUnsupportedVersionTest(versionToImport.toString())
        }

        assertEquals(
            "Version (${KotlinJpsPluginSettings.rawBundledVersion}) of the Kotlin JPS plugin will be used<br>" +
                    "The reason: Kotlin JPS compiler maximum supported version is '$maxVersion' but '$versionToImport' is specified",
            text
        )
    }

    private suspend fun doUnsupportedVersionTest(
        version: String,
        expectedFallbackVersion: String = KotlinJpsPluginSettings.rawBundledVersion
    ) {
        maven.createProjectSubDirs("src/main/kotlin")

        maven.importProjectAsync(
            """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>


                    <build>
                        <sourceDirectory>src/main/kotlin</sourceDirectory>

                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>$version</version>
                            </plugin>
                        </plugins>
                    </build>
                """
        )

        maven.assertModules("project")

        // Fallback to bundled to unsupported version
        assertNotEquals(version, KotlinJpsPluginSettings.jpsVersion(project))
        assertEquals(expectedFallbackVersion, KotlinJpsPluginSettings.jpsVersion(project))
    }

    @Test
    fun testDontShowNotificationWhenBuildIsDelegatedToMaven() = runBlocking {
        val isBuildDelegatedToMaven = MavenRunner.getInstance(project).settings.isDelegateBuildToMaven
        MavenRunner.getInstance(project).settings.isDelegateBuildToMaven = true

        try {
            val version = "1.1.0"
            val notifications = catchNotificationsAsync(project, maven.testRootDisposable) {
                doUnsupportedVersionTest(version, KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler)
            }

            assertNull(notifications.find { it.title == "Unsupported Kotlin JPS plugin version" })
        } finally {
            MavenRunner.getInstance(project).settings.isDelegateBuildToMaven = isBuildDelegatedToMaven
        }
    }
}
