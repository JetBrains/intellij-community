// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.maven.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class CompilerPluginsTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testCompilerPlugins() = runBlocking {
        maven.importProjectAsync(
            """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>$kotlinVersion</version>
                                <configuration>
                                    <compilerPlugins>
                                        <plugin>kotlinx-serialization</plugin>
                                        <plugin>all-open</plugin>
                                        <plugin>lombok</plugin>
                                        <plugin>jpa</plugin>
                                        <plugin>noarg</plugin>
                                        <plugin>sam-with-receiver</plugin>
                                    </compilerPlugins>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                """
        )

        Assertions.assertEquals(
            "",
            facetSettings.compilerSettings!!.additionalArguments
        )
        maven.assertModules("project")
        assertEquals(
            listOf(
                KotlinArtifacts.allopenCompilerPluginPath,
                KotlinArtifacts.kotlinxSerializationCompilerPluginPath,
                KotlinArtifacts.lombokCompilerPluginPath,
                KotlinArtifacts.noargCompilerPluginPath,
                KotlinArtifacts.samWithReceiverCompilerPluginPath,
            ).map { it.toJpsVersionAgnosticKotlinBundledPath() },
            facetSettings.compilerArguments?.pluginClasspaths?.sorted()
        )
    }
}
