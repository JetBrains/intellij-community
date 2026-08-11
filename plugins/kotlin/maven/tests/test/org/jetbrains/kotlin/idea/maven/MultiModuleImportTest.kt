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
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
@KotlinMavenImportingTestBase.MppGoal
class MultiModuleImportTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testMultiModuleImport() = runBlocking {
        maven.createProjectSubDirs(
            "src/main/kotlin",
            "my-common-module/src/main/kotlin",
            "my-jvm-module/src/main/kotlin",
            "my-js-module/src/main/kotlin"
        )

        val mainPom = maven.createProjectPom(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>
            <packaging>pom</packaging>

            <modules>
                <module>my-common-module</module>
                <module>my-jvm-module</module>
                <module>my-js-module</module>
            </modules>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>$kotlinVersion</version>
                    </plugin>
                </plugins>
            </build>
            """
        )

        val commonModule1 = maven.createModulePom(
            "my-common-module1",
            """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-common-module1</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib-common</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>meta</id>
                                    <phase>compile</phase>
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

        val commonModule2 = maven.createModulePom(
            "my-common-module2",
            """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-common-module2</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib-common</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>meta</id>
                                    <phase>compile</phase>
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

        val jvmModule = maven.createModulePom(
            "my-jvm-module",
            """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-jvm-module</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                    <dependency>
                        <groupId>test</groupId>
                        <artifactId>my-common-module1</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                    <dependency>
                        <groupId>test</groupId>
                        <artifactId>my-common-module2</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                </dependencies>

                <build>
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

        val jsModule = maven.createModulePom(
            "my-js-module",
            """

                <parent>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0.0</version>
                </parent>

                <groupId>test</groupId>
                <artifactId>my-js-module</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib-js</artifactId>
                        <version>$kotlinVersion</version>
                    </dependency>
                    <dependency>
                        <groupId>test</groupId>
                        <artifactId>my-common-module1</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>

                            <executions>
                                <execution>
                                    <id>js</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>js</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                    </plugins>
                </build>
                """
        )

        maven.importProjectsAsync(mainPom, commonModule1, commonModule2, jvmModule, jsModule)

        maven.assertModules("project", "my-common-module1", "my-common-module2", "my-jvm-module", "my-js-module")

        with(facetSettings("my-common-module1")) {
            Assertions.assertEquals(CommonPlatforms.defaultCommonPlatform, targetPlatform)
        }

        with(facetSettings("my-common-module2")) {
            Assertions.assertEquals(CommonPlatforms.defaultCommonPlatform, targetPlatform)
        }

        with(facetSettings("my-jvm-module")) {
            Assertions.assertEquals(JvmPlatforms.jvm6, targetPlatform)
            Assertions.assertEquals(listOf("my-common-module1", "my-common-module2"), implementedModuleNames)
        }

        with(facetSettings("my-js-module")) {
            Assertions.assertEquals(JsPlatforms.defaultJsPlatform, targetPlatform)
            Assertions.assertEquals(listOf("my-common-module1"), implementedModuleNames)
        }
    }
}
