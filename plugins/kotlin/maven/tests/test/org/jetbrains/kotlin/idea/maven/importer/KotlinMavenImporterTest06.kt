// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.kotlin.idea.framework.CommonLibraryKind
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMavenImporterTest06 : AbstractKotlinMavenImporterTest() {
    fun testCommonDetectionByGoalWithCommonStdlib() {
        createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        importProject(
            """
            <groupId>test</groupId>0
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
                                    <goal>metadata</goal>
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

        Assert.assertTrue(facetSettings.targetPlatform.isCommon())

        val rootManager = ModuleRootManager.getInstance(getModule("project"))
        val stdlib = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().single().library
        MavenImportingTestCase.assertEquals(CommonLibraryKind, (stdlib as LibraryEx).kind)

        Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

        assertKotlinSources("project", "src/main/kotlin")
        assertKotlinTestSources("project", "src/test/java")
        assertKotlinResources("project", "src/main/resources")
        assertKotlinTestResources("project", "src/test/resources")
    }

    fun testJvmDetectionByConflictingGoalsAndJvmStdlib() {
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
                                    <goal>js</goal>
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

        assertKotlinSources("project", "src/main/kotlin")
        assertKotlinTestSources("project", "src/test/java")
        assertKotlinResources("project", "src/main/resources")
        assertKotlinTestResources("project", "src/test/resources")
    }

    fun testJsDetectionByConflictingGoalsAndJsStdlib() {
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
                                    <goal>js</goal>
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

        Assert.assertTrue(facetSettings.targetPlatform.isJs())

        assertKotlinSources("project", "src/main/kotlin")
        assertKotlinTestSources("project", "src/test/java")
        assertKotlinResources("project", "src/main/resources")
        assertKotlinTestResources("project", "src/test/resources")
    }

    fun testCommonDetectionByConflictingGoalsAndCommonStdlib() {
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
                                    <goal>js</goal>
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

        Assert.assertTrue(facetSettings.targetPlatform.isCommon())

        assertKotlinSources("project", "src/main/kotlin")
        assertKotlinTestSources("project", "src/test/java")
        assertKotlinResources("project", "src/main/resources")
        assertKotlinTestResources("project", "src/test/resources")
    }
}