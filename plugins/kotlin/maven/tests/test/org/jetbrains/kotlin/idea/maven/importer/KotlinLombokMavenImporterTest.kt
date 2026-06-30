// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.maven.KotlinMavenImportingTestBase
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.io.path.absolutePathString

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
@Disabled("Test hangs on buildserver")
class KotlinLombokMavenImporterTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion, createStdProjectFolders = false) {
    @Test
    fun `test kotlin lombok with config import and check plugin classpath and options`() = runBlocking {
        maven.createProjectSubDirs("src/main/kotlin", "src/test/kotlin")
        val absolutePath = maven.createProjectSubFile("lombok.config", "lombok.getter.noisPrefix = true")
            .toNioPath()
            .absolutePathString()

        maven.importProjectAsync(
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

        maven.assertModules("project")

        with(facetSettings) {
            org.junit.Assert.assertEquals(
                "",
                compilerSettings!!.additionalArguments
            )
            assertContain(
                compilerArguments!!.pluginClasspaths!!.toList(),
                KotlinArtifacts.lombokCompilerPluginPath.toString()
            )
            assertEquals(
                compilerArguments!!.pluginOptions!!.toList(),
                listOf(
                    "plugin:org.jetbrains.kotlin.lombok:config=$absolutePath"
                )
            )
        }
    }
}