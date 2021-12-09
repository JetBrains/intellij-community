// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMavenImporterTest04 : AbstractKotlinMavenImporterTest() {
    fun testArgsInFacetInSingleElement() {
        createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        importProject(
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
                            <args>
                                -jvm-target 1.8 -Xcoroutines=enable -classpath "c:\program files\jdk1.8"
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertModules("project")
        assertImporterStatePresent()

        with(facetSettings) {
            org.junit.Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            org.junit.Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            org.junit.Assert.assertEquals("c:/program files/jdk1.8", (compilerArguments as K2JVMCompilerArguments).classpath)
        }
    }

    fun testJvmDetectionByGoalWithJvmStdlib() {
        createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        importProject(
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
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertModules("project")
        assertImporterStatePresent()

        Assert.assertEquals(JvmPlatforms.jvm17, facetSettings.targetPlatform)

        assertSources("project", "src/main/kotlin")
        assertTestSources("project", "src/test/java")
        assertResources("project", "src/main/resources")
        assertTestResources("project", "src/test/resources")
    }

    fun testJvmDetectionByGoalWithJsStdlib() {
        createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        importProject(
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
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertModules("project")
        assertImporterStatePresent()

        Assert.assertEquals(JvmPlatforms.jvm17, facetSettings.targetPlatform)
    }

    fun testJvmDetectionByGoalWithCommonStdlib() {
        createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        importProject(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib-common</artifactId>
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
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>test-compile</id>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertModules("project")
        assertImporterStatePresent()

        Assert.assertEquals(JvmPlatforms.jvm17, facetSettings.targetPlatform)

        assertSources("project", "src/main/kotlin")
        assertTestSources("project", "src/test/java")
        assertResources("project", "src/main/resources")
        assertTestResources("project", "src/test/resources")
    }
}