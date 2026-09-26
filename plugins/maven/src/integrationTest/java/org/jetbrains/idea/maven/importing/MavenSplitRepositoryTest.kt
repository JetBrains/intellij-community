// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import com.intellij.testFramework.UsefulTestCase.assertEmpty

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSplitRepositoryTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  @Test
  fun testSplitRepositoryProjectSync() = runBlocking {
    // configure split repository
    val splitRepositoryOptions = "-Daether.enhancedLocalRepository.split=true -Daether.enhancedLocalRepository.splitLocal=true -Daether.enhancedLocalRepository.splitRemote=true"
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(maven.project)
    settingsComponent.settings.importingSettings.vmOptionsForImporter = splitRepositoryOptions

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>""".trimIndent())

    // validate split repository configuration
    val allConnectors = MavenServerManager.getInstance().getAllConnectors()
    assertEquals(1, allConnectors.size)
    val mavenServerConnector = allConnectors.elementAt(0)
    assertEquals(splitRepositoryOptions, mavenServerConnector.vmOptions)

    // check there were no errors
    val projects = maven.projectsManager.projects
    assertEquals(1, projects.size)
    val project = projects[0]
    val problems = project.problems
    assertEmpty(problems)
  }
}