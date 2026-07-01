// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.maven.AbstractKotlinMavenImporterTest
import org.junit.Test

class KaptMavenProjectConfiguratorTest : AbstractKotlinMavenImporterTest() {
    @Test
    fun testConfigureKotlinAddsKaptToChildModuleWithProcessorVersion() = runBlocking {
        doTestConfigureKotlinAddsKaptToChildModule(
            parentDependencyManagement = "",
            annotationProcessorVersion = """
        <version>1.6.3</version>
      """.trimIndent(),
        )
    }

    @Test
    fun testConfigureKotlinAddsKaptToChildModuleWithManagedProcessorVersion() = runBlocking {
        doTestConfigureKotlinAddsKaptToChildModule(
            parentDependencyManagement = """
        <properties>
          <mapstruct.version>1.6.3</mapstruct.version>
        </properties>

        <dependencyManagement>
          <dependencies>
            <dependency>
              <groupId>org.mapstruct</groupId>
              <artifactId>mapstruct-processor</artifactId>
              <version>$MAPSTRUCT_VERSION_PROPERTY</version>
            </dependency>
          </dependencies>
        </dependencyManagement>
      """.trimIndent(),
            annotationProcessorVersion = "",
        )
    }

    private suspend fun doTestConfigureKotlinAddsKaptToChildModule(
        parentDependencyManagement: String,
        annotationProcessorVersion: String,
    ) {
        createProjectSubDirs("app/src/main/kotlin", "app/src/main/java", "lib/src/main/kotlin")

        val rootPom = createProjectPom(
            """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1.0.0</version>
        <packaging>pom</packaging>

        <$modulesTag>
          <$moduleTag>app</$moduleTag>
          <$moduleTag>lib</$moduleTag>
        </$modulesTag>

        $parentDependencyManagement
      """.trimIndent()
        )

        val appPom = createModulePom(
            "app",
            """
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1.0.0</version>
        </parent>

        <artifactId>app</artifactId>

        <build>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <version>3.13.0</version>
              <configuration>
                <annotationProcessorPaths>
                  <path>
                    <groupId>org.mapstruct</groupId>
                    <artifactId>mapstruct-processor</artifactId>
                    $annotationProcessorVersion
                  </path>
                </annotationProcessorPaths>
              </configuration>
            </plugin>
          </plugins>
        </build>
      """.trimIndent()
        )

        val libPom = createModulePom(
            "lib",
            """
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1.0.0</version>
        </parent>

        <artifactId>lib</artifactId>
      """.trimIndent()
        )

        importProjectsAsync(rootPom, appPom, libPom)
        assertModules("project", "app", "lib")

        configureKotlinForAllModules()

        val rootText = pomText(rootPom)
        val appText = pomText(appPom)
        val libText = pomText(libPom)

        assertContains(rootText, "<artifactId>kotlin-maven-plugin</artifactId>")
        assertNotContains(rootText, "<goal>kapt</goal>")

        assertContains(appText, "<artifactId>kotlin-maven-plugin</artifactId>")
        assertContains(appText, "<goal>kapt</goal>")
        assertContains(appText, "<annotationProcessorPaths>")
        assertContains(appText, "<artifactId>mapstruct-processor</artifactId>")
        assertContains(appText, "<artifactId>kotlin-metadata-jvm</artifactId>")
        assertContains(appText, "<proc>none</proc>")
        assertHasMapstructVersion(appText)

        assertNotContains(libText, "<artifactId>kotlin-maven-plugin</artifactId>")
        assertNotContains(libText, "<goal>kapt</goal>")
    }

    private fun configureKotlinForAllModules() {
        val configurator = KotlinJavaMavenConfigurator()
        runInEdtAndGet {
            configurator.doInternalConfigure(
                project,
                IdeKotlinVersion.get(kotlinVersion),
                listOf(getModule("project"), getModule("app"), getModule("lib")),
                NotificationMessageCollector.create(project),
                configurator.notificationHolder(project),
                emptyMap(),
                emptyMap(),
            ).build()
        }
    }

    private suspend fun pomText(pom: VirtualFile): String = readAction {
        FileDocumentManager.getInstance().getDocument(pom)?.text ?: VfsUtilCore.loadText(pom)
    }

    private fun assertContains(text: String, expected: String) {
        assertTrue("Expected to find '$expected' in:\n$text", text.contains(expected))
    }

    private fun assertNotContains(text: String, unexpected: String) {
        assertFalse("Expected not to find '$unexpected' in:\n$text", text.contains(unexpected))
    }

    private fun assertHasMapstructVersion(text: String) {
        assertTrue(
            "Expected MapStruct processor version in:\n$text",
            text.contains("<version>1.6.3</version>") || text.contains("<version>$MAPSTRUCT_VERSION_PROPERTY</version>")
        )
    }

    companion object {
        private const val MAPSTRUCT_VERSION_PROPERTY = $$"${mapstruct.version}"
    }
}
