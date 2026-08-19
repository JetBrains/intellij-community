// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector.Companion.create
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.maven.KotlinMavenImportingTestBase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Files

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class KotlinJavaMavenConfiguratorCompilerLevelTest(mavenVersion: String, modelVersion: String) :
    KotlinMavenImportingTestBase(mavenVersion, modelVersion) {

    @Test
    fun `does not add compiler release when compiler property is inherited from repository parent`() = timeoutRunBlocking {
        val compilerSetting = "<maven.compiler.release>17</maven.compiler.release>"
        val parentPom = maven.repositoryPath.resolve("org/example/external-parent/1.0/external-parent-1.0.pom")
        Files.createDirectories(parentPom.parent)
        Files.writeString(
            parentPom, """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.example</groupId>
                <artifactId>external-parent</artifactId>
                <version>1.0</version>
                <packaging>pom</packaging>
                <properties>$compilerSetting</properties>
            </project>
            """.trimIndent()
        )

        val configuredPom = configureProjectAndGetPomText(
            """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>external-parent</artifactId>
                <version>1.0</version>
                <relativePath/>
            </parent>
            <artifactId>project</artifactId>
            <version>1.0-SNAPSHOT</version>
            """.trimIndent()
        )

        assertKotlinConfigured(configuredPom)
        assertCompilerReleaseNotAdded(configuredPom)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["release", "source", "target"])
    fun `does not add compiler release when compiler level is configured in an execution`(compilerOption: String) = timeoutRunBlocking {
        val compilerSetting = "<$compilerOption>17</$compilerOption>"
        val configuredPom = configureProjectAndGetPomText(
            projectPomWithCompilerExecutions(compilerExecution("compile", "compile", compilerSetting))
        )

        assertKotlinConfigured(configuredPom)
        assertCompilerReleaseNotAdded(configuredPom)
        assertTrue(configuredPom.contains(compilerSetting))
    }

    @Test
    fun `does not add compiler release when compiler level is inherited in a plugin execution`() = timeoutRunBlocking {
        val compilerSetting = "<target>17</target>"
        val parentPom = maven.createProjectPom(
            """
            <groupId>org.example</groupId>
            <artifactId>parent</artifactId>
            <version>1.0-SNAPSHOT</version>
            <packaging>pom</packaging>
            <modules><module>child</module></modules>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <executions>${compilerExecution("compile", "compile", compilerSetting)}</executions>
                    </plugin>
                </plugins>
            </build>
            """.trimIndent()
        )
        val childPom = maven.createModulePom(
            "child", """
            <parent>
                <groupId>org.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0-SNAPSHOT</version>
            </parent>
            <artifactId>child</artifactId>
            """.trimIndent()
        )

        maven.importProjectsAsync(parentPom, childPom)
        val configuredPom = configureAndGetPomText(project.modules.first { it.name == "child" })

        assertKotlinConfigured(configuredPom)
        assertCompilerReleaseNotAdded(configuredPom)
    }

    @Test
    fun `does not add compiler release when compiler level is configured in the second execution`() = timeoutRunBlocking {
        val compilerSetting = "<target>17</target>"
        // Keep the compiler level only in the second execution to catch implementations that inspect just the first one.
        val configuredPom = configureProjectAndGetPomText(
            projectPomWithCompilerExecutions(
                compilerExecution("compile", "compile", "<debug>true</debug>"),
                compilerExecution("test-compile", "testCompile", compilerSetting)
            )
        )

        assertKotlinConfigured(configuredPom)
        assertCompilerReleaseNotAdded(configuredPom)
        assertTrue(configuredPom.contains(compilerSetting))
    }

    private fun projectPomWithCompilerExecutions(vararg executions: String): String = """
        <groupId>org.example</groupId>
        <artifactId>project</artifactId>
        <version>1.0-SNAPSHOT</version>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <executions>${executions.joinToString("")}</executions>
                </plugin>
            </plugins>
        </build>
    """.trimIndent()

    private fun compilerExecution(id: String, goal: String, configuration: String): String = """
        <execution>
            <id>$id</id>
            <goals><goal>$goal</goal></goals>
            <configuration>$configuration</configuration>
        </execution>
    """.trimIndent()

    private suspend fun configureProjectAndGetPomText(pomContent: String): String {
        val pom = maven.createProjectPom(pomContent)
        maven.importProjectAsync(pom)
        return configureAndGetPomText(project.modules.first { it.name == "project" })
    }

    private suspend fun configureAndGetPomText(module: Module): String = withContext(Dispatchers.EDT) {
        val contentEntryPath = ModuleRootManager.getInstance(module).contentEntries.single().file!!.path
        val pomFile = File(contentEntryPath, "pom.xml")

        val pom = readAction {
            pomFile.toPsiFile(project)!!
        }

        val collector = create(project)
        runConfigurator(module, pom, IdeKotlinVersion.get("2.3.10"), collector)
        collector.showNotification()

        readAction {
            pomFile.toPsiFile(project)!!.text
        }
    }

    private fun runConfigurator(
        module: Module,
        pom: PsiFile,
        version: IdeKotlinVersion,
        collector: NotificationMessageCollector,
    ) {
        val configured = WriteCommandAction.runWriteCommandAction<Boolean>(module.project) {
            KotlinJavaMavenConfigurator().configureModule(module, pom, version, collector)
        }
        assertTrue(configured)
    }

    private fun assertKotlinConfigured(pom: String) {
        assertTrue(pom.contains("<artifactId>kotlin-maven-plugin</artifactId>"))
        assertTrue(pom.contains("<extensions>true</extensions>"))
    }

    private fun assertCompilerReleaseNotAdded(pom: String) {
        assertFalse(pom.contains("<maven.compiler.release>"))
    }
}
