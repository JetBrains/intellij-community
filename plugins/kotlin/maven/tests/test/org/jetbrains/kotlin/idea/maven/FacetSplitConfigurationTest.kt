// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class FacetSplitConfigurationTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testFacetSplitConfiguration() = runBlocking {
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
                                <configuration>
                                    <languageVersion>1.1</languageVersion>
                                    <multiPlatform>true</multiPlatform>
                                    <args>
                                        <arg>-Xcoroutines=enable</arg>
                                    </args>
                                    <classpath>foobar.jar</classpath>
                                </configuration>
                            </execution>
                        </executions>
                        <configuration>
                            <apiVersion>1.0</apiVersion>
                            <nowarn>true</nowarn>
                            <jvmTarget>1.8</jvmTarget>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        maven.assertModules("project")

        with(facetSettings) {
            Assertions.assertEquals("1.1", languageLevel!!.versionString)
            Assertions.assertEquals("1.1", compilerArguments!!.languageVersion)
            Assertions.assertEquals("1.0", apiLevel!!.versionString)
            Assertions.assertEquals("1.0", compilerArguments!!.apiVersion)
            Assertions.assertEquals(true, compilerArguments!!.suppressWarnings)
            Assertions.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            Assertions.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            Assertions.assertEquals("foobar.jar", (compilerArguments as K2JVMCompilerArguments).classpath)
            Assertions.assertEquals("", compilerSettings!!.additionalArguments)
        }
    }
}
