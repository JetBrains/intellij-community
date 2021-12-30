// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.junit.Test
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import kotlin.io.path.absolutePathString

@RunWith(JUnit38ClassRunner::class)
class KotlinLombokMavenImporterTest : AbstractKotlinMavenImporterTest() {
    @Test
    fun `test kotlin lombok import and check plugin classpath`() {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

        importProject(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib</artifactId>
                    <version>1.5.21</version>
                </dependency>
                <dependency>
                    <groupId>org.projectlombok</groupId>
                    <artifactId>lombok</artifactId>
                    <version>1.18.20</version>
                    <scope>provided</scope>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>1.5.21</version>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <sourceDir>src/main/kotlin</sourceDir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>

                        <dependencies>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-lombok</artifactId>
                                <version>1.5.21</version>
                            </dependency>
                        </dependencies>

                        <configuration>
                            <compilerPlugins>
                                <plugin>lombok</plugin>
                            </compilerPlugins>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertModules("project")
        assertImporterStatePresent()

        with(facetSettings) {
            org.junit.Assert.assertEquals(
                "-version",
                compilerSettings!!.additionalArguments
            )
            assertContain(compilerArguments!!.pluginClasspaths!!.toList(), KotlinArtifacts.instance.lombokCompilerPlugin.absolutePath)
        }
    }

    @Test
    fun `test kotlin lombok with config import and check plugin classpath and options`() {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")
        val absolutePath = createProjectSubFile("lombok.config", "lombok.getter.noisPrefix = true")
            .toNioPath()
            .absolutePathString()

        importProject(
            """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1.0.0</version>

            <dependencies>
                <dependency>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-stdlib</artifactId>
                    <version>1.5.21</version>
                </dependency>
                <dependency>
                    <groupId>org.projectlombok</groupId>
                    <artifactId>lombok</artifactId>
                    <version>1.18.20</version>
                    <scope>provided</scope>
                </dependency>
            </dependencies>

            <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>

                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>1.5.21</version>
                        <executions>
                            <execution>
                                <id>compile</id>
                                <goals>
                                    <goal>compile</goal>
                                </goals>
                                <configuration>
                                    <sourceDirs>
                                        <sourceDir>src/main/kotlin</sourceDir>
                                    </sourceDirs>
                                </configuration>
                            </execution>
                        </executions>

                        <dependencies>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-lombok</artifactId>
                                <version>1.5.21</version>
                            </dependency>
                        </dependencies>

                        <configuration>
                            <compilerPlugins>
                                <plugin>lombok</plugin>
                            </compilerPlugins>
                            <pluginOptions>
                                <option>lombok:config=lombok.config</option>
                            </pluginOptions>

                        </configuration>
                    </plugin>
                </plugins>
            </build>
            """
        )

        assertModules("project")
        assertImporterStatePresent()

        with(facetSettings) {
            org.junit.Assert.assertEquals(
                "-version",
                compilerSettings!!.additionalArguments
            )
            assertContain(compilerArguments!!.pluginClasspaths!!.toList(), KotlinArtifacts.instance.lombokCompilerPlugin.absolutePath)
            assertEquals(
                compilerArguments!!.pluginOptions!!.toList(),
                listOf(
                    "plugin:org.jetbrains.kotlin.lombok:config=$absolutePath"
                )
            )
        }
    }
}