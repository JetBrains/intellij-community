// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Test

class MavenSplitRepositoryTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testSplitRepositoryProjectSync() = runBlocking {
    // configure split repository
    val splitRepositoryOptions = "-Daether.enhancedLocalRepository.split=true -Daether.enhancedLocalRepository.splitLocal=true -Daether.enhancedLocalRepository.splitRemote=true"
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(project)
    settingsComponent.settings.importingSettings.vmOptionsForImporter = splitRepositoryOptions

    importProjectAsync("""
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
    val projects = projectsManager.projects
    assertEquals(1, projects.size)
    val project = projects[0]
    val problems = project.problems
    assertEmpty(problems)
  }
}