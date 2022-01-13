// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import junit.framework.TestCase
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAndGetResult
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinMavenImporterTest08 : AbstractKotlinMavenImporterTest() {
    fun testProductionOnTestDependency() {
        createProjectSubDirs(
            "module-with-java/src/main/java",
            "module-with-java/src/test/java",
            "module-with-kotlin/src/main/kotlin",
            "module-with-kotlin/src/test/kotlin"
        )

        val dummyFile = createProjectSubFile(
            "module-with-kotlin/src/main/kotlin/foo/dummy.kt",
            """
                    package foo

                    fun dummy() {
                    }

                """.trimIndent()
        )

        val pomA = createModulePom(
            "module-with-java",
            """
                <parent>
                    <groupId>test-group</groupId>
                    <artifactId>mvnktest</artifactId>
                    <version>0.0.0.0-SNAPSHOT</version>
                </parent>

                <artifactId>module-with-java</artifactId>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-jar-plugin</artifactId>
                            <version>2.6</version>
                            <executions>
                                <execution>
                                    <goals>
                                        <goal>test-jar</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """.trimIndent()
        )

        val pomB = createModulePom(
            "module-with-kotlin",
            """
                <parent>
                    <groupId>test-group</groupId>
                    <artifactId>mvnktest</artifactId>
                    <version>0.0.0.0-SNAPSHOT</version>
                </parent>

                <artifactId>module-with-kotlin</artifactId>

                <properties>
                    <kotlin.version>1.1.4</kotlin.version>
                    <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
                    <kotlin.compiler.incremental>true</kotlin.compiler.incremental>
                </properties>

                <dependencies>

                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${"$"}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-runtime</artifactId>
                        <version>${"$"}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-reflect</artifactId>
                        <version>${"$"}{kotlin.version}</version>
                    </dependency>

                    <dependency>
                        <groupId>test-group</groupId>
                        <artifactId>module-with-java</artifactId>
                    </dependency>

                    <dependency>
                        <groupId>test-group</groupId>
                        <artifactId>module-with-java</artifactId>
                        <type>test-jar</type>
                        <scope>compile</scope>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <version>${"$"}{kotlin.version}</version>
                            <executions>
                                <execution>
                                    <id>compile</id>
                                    <goals> <goal>compile</goal> </goals>
                                    <configuration>
                                        <sourceDirs>
                                            <sourceDir>${"$"}{project.basedir}/src/main/kotlin</sourceDir>
                                            <sourceDir>${"$"}{project.basedir}/src/main/java</sourceDir>
                                        </sourceDirs>
                                    </configuration>
                                </execution>
                                <execution>
                                    <id>test-compile</id>
                                    <goals> <goal>test-compile</goal> </goals>
                                    <configuration>
                                        <sourceDirs>
                                            <sourceDir>${"$"}{project.basedir}/src/test/kotlin</sourceDir>
                                            <sourceDir>${"$"}{project.basedir}/src/test/java</sourceDir>
                                        </sourceDirs>
                                    </configuration>
                                </execution>
                            </executions>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.5.1</version>
                            <executions>
                                <!-- Replacing default-compile as it is treated specially by maven -->
                                <execution>
                                    <id>default-compile</id>
                                    <phase>none</phase>
                                </execution>
                                <!-- Replacing default-testCompile as it is treated specially by maven -->
                                <execution>
                                    <id>default-testCompile</id>
                                    <phase>none</phase>
                                </execution>
                                <execution>
                                    <id>java-compile</id>
                                    <phase>compile</phase>
                                    <goals> <goal>compile</goal> </goals>
                                </execution>
                                <execution>
                                    <id>java-test-compile</id>
                                    <phase>test-compile</phase>
                                    <goals> <goal>testCompile</goal> </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """.trimIndent()
        )

        val pomMain = createModulePom(
            "",
            """
                <groupId>test-group</groupId>
                <artifactId>mvnktest</artifactId>
                <version>0.0.0.0-SNAPSHOT</version>

                <packaging>pom</packaging>

                <properties>
                    <kotlin.version>1.1.4</kotlin.version>
                    <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
                    <kotlin.compiler.incremental>true</kotlin.compiler.incremental>
                </properties>

                <modules>
                    <module>module-with-java</module>
                    <module>module-with-kotlin</module>
                </modules>

                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>test-group</groupId>
                            <artifactId>module-with-kotlin</artifactId>
                            <version>${"$"}{project.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>test-group</groupId>
                            <artifactId>module-with-java</artifactId>
                            <version>${"$"}{project.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>test-group</groupId>
                            <artifactId>module-with-java</artifactId>
                            <version>${"$"}{project.version}</version>
                            <type>test-jar</type>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
                """.trimIndent()
        )

        importProjects(pomMain, pomA, pomB)

        assertModules("module-with-kotlin", "module-with-java", "mvnktest")

        val dependencies = (dummyFile.toPsiFile(myProject) as KtFile).analyzeAndGetResult().moduleDescriptor.allDependencyModules
        TestCase.assertTrue(dependencies.any { it.name.asString() == "<production sources for module module-with-java>" })
        TestCase.assertTrue(dependencies.any { it.name.asString() == "<test sources for module module-with-java>" })
    }

    fun testNoArgDuplication() {
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
                                <arg>-Xjsr305=strict</arg>
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
            Assert.assertEquals("-Xjsr305=strict", compilerSettings!!.additionalArguments)
        }
    }

    fun testInternalArgumentsFacetImporting() {
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
                            <languageVersion>1.2</languageVersion>
                            <args>
                                <arg>-XXLanguage:+InlineClasses</arg>
                            </args>
                            <jvmTarget>1.8</jvmTarget>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertImporterStatePresent()

        // Check that we haven't lost internal argument during importing to facet
        Assert.assertEquals("-XXLanguage:+InlineClasses", facetSettings.compilerSettings?.additionalArguments)

        // Check that internal argument influenced LanguageVersionSettings correctly
        Assert.assertEquals(
            LanguageFeature.State.ENABLED,
            getModule("project").languageVersionSettings.getFeatureSupport(LanguageFeature.InlineClasses)
        )
    }

    fun testStableModuleNameWhileUsingMaven_JVM() {
        createProjectSubDirs("src/main/kotlin")

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
                            <languageVersion>1.2</languageVersion>
                            <jvmTarget>1.8</jvmTarget>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertImporterStatePresent()

        checkStableModuleName("project", "project", JvmPlatforms.unspecifiedJvmPlatform, isProduction = true)
        checkStableModuleName("project", "project", JvmPlatforms.unspecifiedJvmPlatform, isProduction = false)
    }
}