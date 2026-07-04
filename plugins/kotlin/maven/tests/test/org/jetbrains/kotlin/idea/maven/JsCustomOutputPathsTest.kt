// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.PathUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.junit.Assert
import org.junit.Assert.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
@KotlinMavenImportingTestBase.MppGoal
class JsCustomOutputPathsTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJsCustomOutputPaths() = runBlocking {
        maven.createProjectSubDirs("src/main/kotlin", "src/test/kotlin")
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
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <version>$kotlinVersion</version>

                        <executions>
                            <execution>
                                <id>compile</id>
                                <phase>compile</phase>
                                <goals>
                                    <goal>js</goal>
                                </goals>
                                <configuration>
                                    <outputFile>${'$'}{project.basedir}/prod/main.js</outputFile>
                                </configuration>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-js</goal>
                                </goals>
                                <configuration>
                                    <outputFile>${'$'}{project.basedir}/test/test.js</outputFile>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertNotEquals(kotlinVersion, KotlinJpsPluginSettings.jpsVersion(project))
        Assert.assertEquals(KotlinJpsPluginSettings.fallbackVersionForOutdatedCompiler, KotlinJpsPluginSettings.jpsVersion(project))

        maven.assertModules("project")

        val projectBasePath = projectsManager.projects.first().file.parent.path

        with(facetSettings) {
            Assert.assertEquals(
                "$projectBasePath/prod/main.js",
                PathUtil.toSystemIndependentName(productionOutputPath)
            )
            Assert.assertEquals(
                "$projectBasePath/test/test.js",
                PathUtil.toSystemIndependentName(testOutputPath)
            )
        }

        with(CompilerModuleExtension.getInstance(maven.getModule("project"))!!) {
            Assert.assertEquals("$projectBasePath/prod", PathUtil.toSystemIndependentName(compilerOutputUrl))
            Assert.assertEquals("$projectBasePath/test", PathUtil.toSystemIndependentName(compilerOutputUrlForTests))
        }
    }
}
