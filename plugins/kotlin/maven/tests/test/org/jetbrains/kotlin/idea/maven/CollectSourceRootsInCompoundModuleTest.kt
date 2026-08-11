// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class CollectSourceRootsInCompoundModuleTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {
    @Test
    fun testCollectSourceRootsInCompoundModule() = runBlocking {
        maven.createProjectSubDirs("src/main/kotlin", "src/main/kotlin.jvm", "src/test/kotlin", "src/test/kotlin.jvm")

        maven.importProjectAsync(
            """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>8</maven.compiler.release>
                        <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                            </plugin>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>1.8.10</version>
                                <executions>
                                    <execution>
                                        <id>compile</id>
                                        <goals>
                                            <goal>compile</goal>
                                        </goals>
                                        <configuration>
                                            <sourceDirs>
                                                <sourceDir>${"$"}{project.basedir}/src/main/kotlin</sourceDir>
                                                <sourceDir>${"$"}{project.basedir}/src/main/java</sourceDir>
                                            </sourceDirs>
                                        </configuration>
                                    </execution>
                                    <execution>
                                        <id>test-compile</id>
                                        <goals>
                                            <goal>test-compile</goal>
                                        </goals>
                                        <configuration>
                                            <sourceDirs>
                                                <sourceDir>${"$"}{project.basedir}/src/test/kotlin</sourceDir>
                                                <sourceDir>${"$"}{project.basedir}/src/test/java</sourceDir>
                                            </sourceDirs>
                                        </configuration>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
            """
        )

        val mainModule = "project.main"
        val testModule = "project.test"
        val compoundModule = "project"

        maven.assertModules(compoundModule, mainModule, testModule)

        maven.assertSources(mainModule, "src/main/kotlin", "src/main/java")
        maven.assertDefaultResources(mainModule)

        maven.assertTestSources(testModule,"src/test/kotlin", "src/test/java")
        maven.assertDefaultTestResources(testModule)
    }
}
