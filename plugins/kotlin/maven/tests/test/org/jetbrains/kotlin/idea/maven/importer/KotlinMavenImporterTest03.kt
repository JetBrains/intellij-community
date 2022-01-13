// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.framework.JSLibraryKind
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMavenImporterTest03 : AbstractKotlinMavenImporterTest() {
    fun testJvmFacetConfigurationFromProperties() {
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

        assertModules("project")
        assertImporterStatePresent()

        with(facetSettings) {
            Assert.assertEquals("1.0", languageLevel!!.versionString)
            Assert.assertEquals("1.0", compilerArguments!!.languageVersion)
            Assert.assertEquals("1.0", apiLevel!!.versionString)
            Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
            Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
        }

        assertSources("project", "src/main/kotlin")
        assertTestSources("project", "src/test/java")
        assertResources("project", "src/main/resources")
        assertTestResources("project", "src/test/resources")
    }

    fun testJsFacetConfiguration() {
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

        assertModules("project")
        assertImporterStatePresent()

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
            Assert.assertEquals(
                "-meta-info -output test.js",
                compilerSettings!!.additionalArguments
            )
        }

        val rootManager = ModuleRootManager.getInstance(getModule("project"))
        val stdlib = rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().single().library
        assertEquals(JSLibraryKind, (stdlib as LibraryEx).kind)

        Assert.assertTrue(ModuleRootManager.getInstance(getModule("project")).sdk!!.sdkType is KotlinSdkType)

        assertKotlinSources("project", "src/main/kotlin")
        assertKotlinTestSources("project", "src/test/java")
        assertKotlinResources("project", "src/main/resources")
        assertKotlinTestResources("project", "src/test/resources")
    }

    fun testJsCustomOutputPaths() {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")
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

        assertModules("project")
        assertImporterStatePresent()

        val projectBasePath = myProjectsManager.projects.first().file.parent.path

        with(facetSettings) {
            Assert.assertEquals("$projectBasePath/prod/main.js", PathUtil.toSystemIndependentName(productionOutputPath))
            Assert.assertEquals("$projectBasePath/test/test.js", PathUtil.toSystemIndependentName(testOutputPath))
        }

        with(CompilerModuleExtension.getInstance(getModule("project"))!!) {
            Assert.assertEquals("$projectBasePath/prod", PathUtil.toSystemIndependentName(compilerOutputUrl))
            Assert.assertEquals("$projectBasePath/test", PathUtil.toSystemIndependentName(compilerOutputUrlForTests))
        }
    }

    fun testFacetSplitConfiguration() {
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

        assertModules("project")
        assertImporterStatePresent()

        with(facetSettings) {
            Assert.assertEquals("1.1", languageLevel!!.versionString)
            Assert.assertEquals("1.1", compilerArguments!!.languageVersion)
            Assert.assertEquals("1.0", apiLevel!!.versionString)
            Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
            Assert.assertEquals(true, compilerArguments!!.suppressWarnings)
            Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            Assert.assertEquals("foobar.jar", (compilerArguments as K2JVMCompilerArguments).classpath)
            Assert.assertEquals("-version", compilerSettings!!.additionalArguments)
        }
    }
}