// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import kotlinx.coroutines.runBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.assertDoNotContain
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class RepositoriesImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testMirrorCentralImport() = runBlocking {
    val oldSettingsFile = maven.mavenGeneralSettings.userSettingsFile
    try {
      val settingsXml = maven.createProjectSubFile("settings.xml", """
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
      maven.refreshFiles(listOf(settingsXml))
      maven.mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

      maven.importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())

      assertHaveRepositories("https://example.com/maven2")
    }
    finally {
      maven.mavenGeneralSettings.setUserSettingsFile(oldSettingsFile)
    }
  }

  @Test
  fun testMirrorAllImport() = runBlocking {
    val oldSettingsFile = maven.mavenGeneralSettings.userSettingsFile
    try {
      val settingsXml = maven.createProjectSubFile("settings.xml", """
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
      maven.refreshFiles(listOf(settingsXml))
      maven.mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

      maven.importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())

      assertHaveRepositories("https://example.com/maven2")
    }
    finally {
      maven.mavenGeneralSettings.setUserSettingsFile(oldSettingsFile)
    }
  }

  @Test
  fun testMirrorAllExceptCentralImport() = runBlocking {
    val oldSettingsFile = maven.mavenGeneralSettings.userSettingsFile
    try {
      val settingsXml = maven.createProjectSubFile("settings.xml", """
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
      maven.refreshFiles(listOf(settingsXml))
      maven.mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

      maven.importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())

      assertHaveRepositories("https://repo1.maven.org/maven2")
      assertDoNotHaveRepositories("https://example.com/maven2<")
    }
    finally {
      maven.mavenGeneralSettings.setUserSettingsFile(oldSettingsFile)
    }
  }

  private fun assertDoNotHaveRepositories(vararg repos: String) {
    val actual = RemoteRepositoriesConfiguration.getInstance(maven.project).repositories.map { it.url }

    assertDoNotContain(actual, *repos)
  }


  private fun assertHaveRepositories(vararg repos: String) {
    val actual = RemoteRepositoriesConfiguration.getInstance(maven.project).repositories.map { it.url }

    assertContain(actual, *repos)
  }
}
