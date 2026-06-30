// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.isJs
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
@KotlinMavenImportingTestBase.MppGoal
class JsFacetConfigurationTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJsFacetConfiguration() = runBlocking {
        maven.createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        maven.importProjectAsync(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-js</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>

                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <languageVersion>1.1</languageVersion>
                            <apiVersion>1.0</apiVersion>
                            <multiPlatform>true</multiPlatform>
                            <nowarn>true</nowarn>
                            <args>
                                <arg>-Xcoroutines=enable</arg>
                            </args>
                            <sourceMap>true</sourceMap>
                            <outputFile>test.js</outputFile>
                            <metaInfo>true</metaInfo>
                            <moduleKind>commonjs</moduleKind>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        maven.assertModules("project")

        with(facetSettings) {
            Assert.assertEquals("1.1", languageLevel!!.versionString)
            Assert.assertEquals("1.1", compilerArguments!!.languageVersion)
            Assert.assertEquals("1.0", apiLevel!!.versionString)
            Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
            Assert.assertFalse(compilerArguments!!.autoAdvanceLanguageVersion)
            Assert.assertFalse(compilerArguments!!.autoAdvanceApiVersion)
            Assert.assertEquals(true, compilerArguments!!.suppressWarnings)
            Assert.assertTrue(targetPlatform.isJs())
            with(compilerArguments as K2JSCompilerArguments) {
                Assert.assertEquals(true, sourceMap)
                Assert.assertEquals("commonjs", moduleKind)
            }
            Assert.assertEquals("", compilerSettings!!.additionalArguments)
        }

        val rootManager = ModuleRootManager.getInstance(maven.getModule("project"))
        val stdlib = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().single().library
        assertEquals(KotlinJavaScriptLibraryKind, (stdlib as LibraryEx).kind)

        Assert.assertTrue(ModuleRootManager.getInstance(maven.getModule("project")).sdk!!.sdkType is KotlinSdkType)

        assertKotlinSources("project", "src/main/kotlin")
        assertKotlinTestSources("project", "src/test/java")
        assertDefaultKotlinResources("project")
        assertDefaultKotlinTestResources("project")
    }
}
