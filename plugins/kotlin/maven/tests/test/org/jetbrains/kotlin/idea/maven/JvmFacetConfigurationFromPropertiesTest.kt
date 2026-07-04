// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class JvmFacetConfigurationFromPropertiesTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testJvmFacetConfigurationFromProperties() = runBlocking {
        maven.createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        maven.importProjectAsync(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib</artifactId>
                    <version>$kotlinVersion</version>
                </dependency>
            </dependencies>

            <properties>
                <kotlin.compiler.languageVersion>1.0</kotlin.compiler.languageVersion>
                <kotlin.compiler.apiVersion>1.0</kotlin.compiler.apiVersion>
                <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
            </properties>

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
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        )

        maven.assertModules("project")

        with(facetSettings) {
            Assert.assertEquals("1.0", languageLevel!!.versionString)
            Assert.assertEquals("1.0", compilerArguments!!.languageVersion)
            Assert.assertEquals("1.0", apiLevel!!.versionString)
            Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
            Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
        }

        maven.assertSources("project", "src/main/kotlin")
        maven.assertTestSources("project", "src/test/java")
        maven.assertDefaultResources("project")
        maven.assertDefaultTestResources("project")
    }
}
