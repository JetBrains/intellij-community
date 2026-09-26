// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.additionalArgumentsAsList
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class SubmoduleArgsInheritanceTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testSubmoduleArgsInheritance() = runBlocking {
        maven.createProjectSubDirs("src/main/kotlin", "myModule1/src/main/kotlin", "myModule2/src/main/kotlin", "myModule3/src/main/kotlin")

        val mainPom = maven.createProjectPom(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>
            <packaging>pom</packaging>

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
                            <jvmTarget>1.7</jvmTarget>
                            <languageVersion>1.1</languageVersion>
                            <apiVersion>1.0</apiVersion>
                            <args>
                                <arg>-java-parameters</arg>
                                <arg>-Xjava-source-roots=javaDir</arg>
                                <arg>-kotlin-home</arg>
                                <arg>temp</arg>
                            </args>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        val modulePom1 = maven.createModulePom(
            "myModule1",
            """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>myModule1</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <sourceDirectory>myModule1/src/main/kotlin</sourceDirectory>

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
                                <args>
                                    <arg>-Xjava-source-roots=javaDir2</arg>
                                </args>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
        )

        val modulePom2 = maven.createModulePom(
            "myModule2",
            """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>myModule2</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <sourceDirectory>myModule2/src/main/kotlin</sourceDirectory>

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
                                <args combine.children="append">
                                    <arg>-kotlin-home</arg>
                                    <arg>temp2</arg>
                                </args>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
        )

        val modulePom3 = maven.createModulePom(
            "myModule3",
            """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>myModule3</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <sourceDirectory>myModule3/src/main/kotlin</sourceDirectory>

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

                            <configuration combine.self="override">
                                <jvmTarget>1.8</jvmTarget>
                                <args>
                                    <arg>-kotlin-home</arg>
                                    <arg>temp2</arg>
                                </args>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
                """
        )

        maven.importProjectsAsync(mainPom, modulePom1, modulePom2, modulePom3)

        maven.assertModules("project", "myModule1", "myModule2", "myModule3")

        with(facetSettings("myModule1")) {
            Assertions.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            Assertions.assertEquals(LanguageVersion.KOTLIN_1_1, languageLevel!!)
            Assertions.assertEquals(LanguageVersion.KOTLIN_1_0, apiLevel!!)
            Assertions.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            Assertions.assertEquals(
                listOf("-Xjava-source-roots=javaDir2"),
                compilerSettings!!.additionalArgumentsAsList
            )
        }

        with(facetSettings("myModule2")) {
            Assertions.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            Assertions.assertEquals(LanguageVersion.KOTLIN_1_1, languageLevel!!)
            Assertions.assertEquals(LanguageVersion.KOTLIN_1_0, apiLevel!!)
            Assertions.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            Assertions.assertEquals(
                listOf("-java-parameters", "-Xjava-source-roots=javaDir", "-kotlin-home", "temp2"),
                compilerSettings!!.additionalArgumentsAsList
            )
        }

        with(facetSettings("myModule3")) {
            Assertions.assertEquals("JVM 1.8", targetPlatform!!.oldFashionedDescription)
            Assertions.assertEquals(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, languageLevel)
            Assertions.assertEquals(KotlinPluginLayout.standaloneCompilerVersion.languageVersion, apiLevel)
            Assertions.assertEquals("1.8", (compilerArguments as K2JVMCompilerArguments).jvmTarget)
            Assertions.assertEquals(
                listOf("-kotlin-home", "temp2"),
                compilerSettings!!.additionalArgumentsAsList
            )
        }
    }
}
