// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerCMDState

class InvalidEnvironmentImportingTest : MavenImportingTestCase() {
  private lateinit var myTestSyncViewManager: SyncViewManager
  private val myEvents: MutableList<BuildEvent> = ArrayList()

  public override fun setUp() {
    super.setUp()
    myTestSyncViewManager = object : SyncViewManager(myProject) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        myEvents.add(event)
      }
    }
    myProjectsManager.setProgressListener(myTestSyncViewManager);
  }

  fun testShouldShowWarningIfBadJDK() {
    val projectSdk = ProjectRootManager.getInstance(myProject).projectSdk
    val jdkForImporter = MavenWorkspaceSettingsComponent.getInstance(myProject).settings.importingSettings.jdkForImporter
    try {
      MavenWorkspaceSettingsComponent.getInstance(
        myProject).settings.importingSettings.jdkForImporter = MavenRunnerSettings.USE_PROJECT_JDK;
      WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(myProject).projectSdk = null }
      createAndImportProject()
      assertEvent { it.message.startsWith("Project JDK is not specified") }
    }
    finally {
      WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(myProject).projectSdk = projectSdk }
      MavenWorkspaceSettingsComponent.getInstance(myProject).settings.importingSettings.jdkForImporter = jdkForImporter
    }
  }

  fun testShouldShowLogsOfMavenServerIfNotStarted() {
    try {
      MavenServerCMDState.setThrowExceptionOnNextServerStart()
      createAndImportProject()
      assertEvent { it.message.contains("Maven server exception for tests") }
    } finally {
      MavenServerCMDState.resetThrowExceptionOnNextServerStart()
    }
  }


  private fun assertEvent(description: String = "Asserted", predicate: (BuildEvent) -> Boolean) {
    if (myEvents.isEmpty()) {
      fail("Message \"${description}\" was not found. No messages was recorded at all")
    }
    if (myEvents.any(predicate)) {
      return
    }

    fail("Message \"${description}\" was not found. Known messages:\n" +
         myEvents.joinToString("\n") { "${it}" });
  }

  private fun assertErrorEvent(message: String) {

  }

  private fun createAndImportProject() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>test</artifactId>" +
                     "<version>1.0</version>")
    importProjectWithErrors()
  }
}