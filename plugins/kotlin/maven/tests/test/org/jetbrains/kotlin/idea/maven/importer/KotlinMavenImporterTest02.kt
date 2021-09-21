// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import com.intellij.application.options.CodeStyle
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMavenImporterTest02 : AbstractKotlinMavenImporterTest() {

    fun testImportOfficialCodeStyle() {
        importProject(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <properties>
                <kotlin.code.style>official</kotlin.code.style>
            </properties>
            """
        )

        Assert.assertEquals(
            KotlinStyleGuideCodeStyle.CODE_STYLE_ID,
            CodeStyle.getSettings(myProject).kotlinCodeStyleDefaults()
        )
    }

    fun testReImportRemoveDir() {
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
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                        <dir>src/main/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
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

        assertSources("project", "src/main/kotlin", "src/main/kotlin.jvm")
        assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")

        // reimport
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
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertSources("project", "src/main/kotlin")
        assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")
    }

    fun testReImportAddDir() {
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
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
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

        assertSources("project", "src/main/kotlin")
        assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")

        // reimport
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
                                    <sourceDirs>
                                        <dir>src/main/kotlin</dir>
                                        <dir>src/main/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>

                            <execution>
                                <id>test-compile</id>
                                <phase>test-compile</phase>
                                <goals>
                                    <goal>test-compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <dir>src/test/kotlin</dir>
                                        <dir>src/test/kotlin.jvm</dir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertSources("project", "src/main/kotlin", "src/main/kotlin.jvm")
        assertTestSources("project", "src/test/java", "src/test/kotlin", "src/test/kotlin.jvm")
    }

    fun testJvmFacetConfiguration() {
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
                            <languageVersion>1.1</languageVersion>
                            <apiVersion>1.0</apiVersion>
                            <multiPlatform>true</multiPlatform>
                            <nowarn>true</nowarn>
                            <args>
                                <arg>-Xcoroutines=enable</arg>
                            </args>
                            <jvmTarget>1.8</jvmTarget>
                            <classpath>foobar.jar</classpath>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertModules("project")
        assertImporterStatePresent()

        with(facetSettings) {
            org.junit.Assert.assertEquals("1.1", languageLevel!!.versionString)
            org.junit.Assert.assertEquals("1.1", compilerArguments!!.languageVersion)
            org.junit.Assert.assertEquals("1.0", apiLevel!!.versionString)
            org.junit.Assert.assertEquals("1.0", compilerArguments!!.apiVersion)
            org.junit.Assert.assertFalse(compilerArguments!!.autoAdvanceLanguageVersion)
            org.junit.Assert.assertFalse(compilerArguments!!.autoAdvanceApiVersion)
            org.junit.Assert.assertEquals(true, compilerArguments!!.suppressWarnings)
            org.junit.Assert.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            org.junit.Assert.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            org.junit.Assert.assertEquals("foobar.jar", (compilerArguments as K2JVMCompilerArguments).classpath)
            org.junit.Assert.assertEquals(
                "-version",
                compilerSettings!!.additionalArguments
            )
        }

        assertSources("project", "src/main/kotlin")
        assertTestSources("project", "src/test/java")
        assertResources("project", "src/main/resources")
        assertTestResources("project", "src/test/resources")
    }
}