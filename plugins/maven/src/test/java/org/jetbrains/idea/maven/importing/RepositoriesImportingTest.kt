// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RepositoriesImportingTest : MavenMultiVersionImportingTestCase() {


  @Test
  fun importSimpleProject() = runBlocking {
    importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())
    val mavenProject = projectsManager.findProject(projectPom)
    assertNotNull(mavenProject)
    UsefulTestCase.assertSameElements(mavenProject!!.remoteRepositories.map { it.url }, "https://repo.maven.apache.org/maven2")
  }

  @Test
  fun testMirrorCentralImport() = runBlocking {
    val oldSettingsFile = mavenGeneralSettings.userSettingsFile
    try {
      val settingsXml = createProjectSubFile("settings.xml", """
        <settings>
        <mirrors>
            <mirror>
              <id>central-mirror</id>
              <name>mirror</name>
              <url>https://example.com/maven2</url>
              <mirrorOf>central</mirrorOf>
            </mirror> 
          </mirrors>
        </settings>
        """.trimIndent())
      refreshFiles(listOf(settingsXml))
      mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())

      assertHaveRepositories("https://example.com/maven2")
    }
    finally {
      mavenGeneralSettings.setUserSettingsFile(oldSettingsFile)
    }
  }

  @Test
  fun testMirrorAllImport() = runBlocking {
    val oldSettingsFile = mavenGeneralSettings.userSettingsFile
    try {
      val settingsXml = createProjectSubFile("settings.xml", """
        <settings>
        <mirrors>
            <mirror>
              <id>central-mirror</id>
              <name>mirror</name>
              <url>https://example.com/maven2</url>
              <mirrorOf>*</mirrorOf>
            </mirror>
          </mirrors>
        </settings>
        """.trimIndent())
      refreshFiles(listOf(settingsXml))
      mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())

      assertHaveRepositories("https://example.com/maven2")
    }
    finally {
      mavenGeneralSettings.setUserSettingsFile(oldSettingsFile)
    }
  }

  @Test
  fun testMirrorAllExceptCentralImport() = runBlocking {
    val oldSettingsFile = mavenGeneralSettings.userSettingsFile
    try {
      val settingsXml = createProjectSubFile("settings.xml", """
        <settings>
        <mirrors>
            <mirror>
              <id>central-mirror</id>
              <name>mirror</name>
              <url>https://example.com/maven2</url>
              <mirrorOf>*,!central</mirrorOf>
            </mirror>
          </mirrors>
        </settings>
        """.trimIndent())
      refreshFiles(listOf(settingsXml))
      mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())

      assertHaveRepositories("https://repo1.maven.org/maven2")
      assertDoNotHaveRepositories("https://example.com/maven2<")
    }
    finally {
      mavenGeneralSettings.setUserSettingsFile(oldSettingsFile)
    }
  }

  private fun assertDoNotHaveRepositories(vararg repos: String) {
    val actual = RemoteRepositoriesConfiguration.getInstance(project).repositories.map { it.url }

    assertDoNotContain(actual, *repos)
  }


  private fun assertHaveRepositories(vararg repos: String) {
    val actual = RemoteRepositoriesConfiguration.getInstance(project).repositories.map { it.url }

    assertContain(actual, *repos)
  }
}
