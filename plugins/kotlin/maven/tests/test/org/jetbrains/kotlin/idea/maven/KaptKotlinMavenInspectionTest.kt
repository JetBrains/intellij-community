// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.setupJdkForModule
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.inspections.KaptKotlinCompilerPluginInspection
import org.jetbrains.kotlin.idea.maven.configuration.KaptMavenKotlinCompilerPluginProjectConfigurator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Files

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
internal class KaptKotlinMavenInspectionTest(mavenVersion: String, modelVersion: String) :
    AbstractMavenUpdateConfigurationQuickFixTest(mavenVersion, modelVersion) {

    override val testRoot: String
        get() = "maven/tests/testData/kapt/fixes/"

    @BeforeEach
    fun enableInspections() {
        codeInsightTestFixture.enableInspections(KaptKotlinCompilerPluginInspection::class.java)
    }

    @Test
    fun testAddKaptCompilerPluginForMapstructProcessorDependency() = runBlocking {
        doMultiFileTest()
    }

    @Test
    fun testNoKaptCompilerPluginInspectionWhenKspConfigured() = runBlocking {
        doMultiFileTest {
            withContext(Dispatchers.EDT) {
                writeIntentReadAction {
                    assertTrue(
                        codeInsightTestFixture.filterAvailableIntentions("Add Kotlin kapt compiler plugin").isEmpty()
                    )
                }
            }
        }
    }

    @Test
    fun testNoKaptCompilerPluginInspectionWhenKaptInheritedFromParentPluginManagement() = runBlocking {
        doMultiFileTest {
            withContext(Dispatchers.EDT) {
                writeIntentReadAction {
                    assertTrue(
                        codeInsightTestFixture.filterAvailableIntentions("Add Kotlin kapt compiler plugin").isEmpty(),
                        "kapt execution is inherited from the parent's <pluginManagement>, so the inspection must not fire"
                    )
                }
            }
        }
    }

    @Test
    fun testKaptInspectionFiresWhenParentHasProcessorPathButNoKapt() = runBlocking {
        doMultiFileTest {
            withContext(Dispatchers.EDT) {
                writeIntentReadAction {
                    assertTrue(
                        codeInsightTestFixture.filterAvailableIntentions("Add Kotlin kapt compiler plugin").isNotEmpty(),
                        "annotation processor is inherited from the parent and kapt is absent, so the inspection must fire"
                    )
                }
            }
        }
    }

    @Test
    fun testNoKaptInspectionWhenExternalParentManagesKapt() = runBlocking {
        importExternalParent(hasKaptExecution = true)

        withContext(Dispatchers.EDT) {
            writeIntentReadAction {
                assertTrue(codeInsightTestFixture.filterAvailableIntentions("Add Kotlin kapt compiler plugin").isEmpty())
            }
        }
    }

    @Test
    fun testKaptInspectionFiresWhenExternalParentHasProcessorPathButNoKapt() = runBlocking {
        val module = importExternalParent(hasKaptExecution = false)

        withContext(Dispatchers.EDT) {
            writeIntentReadAction {
                assertTrue(KaptMavenKotlinCompilerPluginProjectConfigurator().isApplicable(module))
                assertTrue(codeInsightTestFixture.filterAvailableIntentions("Add Kotlin kapt compiler plugin").isNotEmpty())
            }
        }
    }

    private suspend fun importExternalParent(hasKaptExecution: Boolean): Module {
        val artifactId = if (hasKaptExecution) "kapt-external-parent" else "kapt-external-parent-without-kapt"
        val kaptExecution = if (hasKaptExecution) """
            <execution>
                <id>kapt</id>
                <goals><goal>kapt</goal></goals>
            </execution>
        """.trimIndent() else ""
        val parentPom = maven.repositoryPath.resolve("org/example/$artifactId/1.0/$artifactId-1.0.pom")
        Files.createDirectories(parentPom.parent)
        Files.writeString(parentPom, """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.example</groupId>
                <artifactId>$artifactId</artifactId>
                <version>1.0</version>
                <packaging>pom</packaging>
                <build>
                    <pluginManagement>
                        <plugins>
                            <plugin>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-maven-plugin</artifactId>
                                <version>2.4.0</version>
                                <executions>
                                    $kaptExecution
                                    <execution>
                                        <id>compile</id>
                                        <phase>compile</phase>
                                        <goals><goal>compile</goal></goals>
                                    </execution>
                                    <execution>
                                        <id>test-compile</id>
                                        <phase>test-compile</phase>
                                        <goals><goal>test-compile</goal></goals>
                                    </execution>
                                </executions>
                            </plugin>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.14.1</version>
                                <configuration>
                                    <annotationProcessorPaths>
                                        <path>
                                            <groupId>org.mapstruct</groupId>
                                            <artifactId>mapstruct-processor</artifactId>
                                            <version>1.6.3</version>
                                        </path>
                                    </annotationProcessorPaths>
                                </configuration>
                            </plugin>
                        </plugins>
                    </pluginManagement>
                </build>
            </project>
        """.trimIndent())

        val pom = maven.createProjectSubFile("pom.xml", """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.example</groupId>
                    <artifactId>$artifactId</artifactId>
                    <version>1.0</version>
                    <relativePath/>
                </parent>
                <artifactId>consumer</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>2.4.0</version>
                    </dependency>
                </dependencies>
                <build>
                    <sourceDirectory>src/main/kotlin</sourceDirectory>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
        """.trimIndent())
        val source = maven.createProjectSubFile("src/main/kotlin/demo/Demo.kt", "package demo\nclass Demo")
        maven.projectPom = pom
        maven.importProjectAsync(pom)

        val module = project.modules.single()
        val mavenProject = requireNotNull(MavenProjectsManager.getInstance(project).findProject(module))
        assertTrue(mavenProject.externalAnnotationProcessors.any { it.artifactId == "mapstruct-processor" })
        val effectivePlugin = requireNotNull(mavenProject.findPlugin("org.jetbrains.kotlin", "kotlin-maven-plugin"))
        assertEquals(hasKaptExecution, effectivePlugin.executions.any { "kapt" in it.goals })

        withContext(Dispatchers.EDT) {
            edtWriteAction { maven.setupJdkForModule(module.name) }
            writeIntentReadAction {
                assertTrue(ModuleRootManager.getInstance(module).fileIndex.isInSourceContent(source))
                codeInsightTestFixture.configureFromExistingVirtualFile(source)
            }
        }
        return module
    }
}
