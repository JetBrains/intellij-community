// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ArgsOverridingInFacetTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testArgsOverridingInFacet() = runBlocking {
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
                        <configuration>
                            <jvmTarget>1.8</jvmTarget>
                            <languageVersion>1.0</languageVersion>
                            <apiVersion>1.0</apiVersion>
                            <args>
                                <arg>-jvm-target</arg>
                                <arg>11</arg>
                                <arg>-language-version</arg>
                                <arg>1.1</arg>
                                <arg>-api-version</arg>
                                <arg>1.1</arg>
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        maven.assertModules("project")

        with(facetSettings) {
            Assert.assertEquals("JVM 11", targetPlatform!!.oldFashionedDescription)
            Assert.assertEquals(LanguageVersion.KOTLIN_1_1.description, languageLevel!!.description)
            Assert.assertEquals(LanguageVersion.KOTLIN_1_1.description, apiLevel!!.description)
            Assert.assertEquals("11", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
        }
    }
}
