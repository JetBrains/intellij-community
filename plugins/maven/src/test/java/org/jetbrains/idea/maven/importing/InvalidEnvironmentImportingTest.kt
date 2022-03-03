// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.LoggedErrorProcessor
import junit.framework.TestCase
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerCMDState
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Test

class InvalidEnvironmentImportingTest : MavenMultiVersionImportingTestCase() {
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

  @Test
  fun testShouldShowWarningIfProjectJDKIsNullAndRollbackToInternal() {
    val projectSdk = ProjectRootManager.getInstance(myProject).projectSdk
    val jdkForImporter = MavenWorkspaceSettingsComponent.getInstance(myProject).settings.importingSettings.jdkForImporter
    try {
      LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("Project JDK is not specifie")) {
        MavenWorkspaceSettingsComponent.getInstance(myProject)
          .settings.getImportingSettings().jdkForImporter = MavenRunnerSettings.USE_PROJECT_JDK;
        WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(myProject).projectSdk = null }
        createAndImportProject()
        val connectors = MavenServerManager.getInstance().allConnectors.filter { it.project == myProject }
        assertNotEmpty(connectors)
        TestCase.assertEquals(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk(), connectors[0].jdk);
      }
    }
    finally {
      WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(myProject).projectSdk = projectSdk }
      MavenWorkspaceSettingsComponent.getInstance(myProject).settings.importingSettings.jdkForImporter = jdkForImporter
    }
  }

  @Test
  fun testShouldShowLogsOfMavenServerIfNotStarted() {
    try {
      LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("Maven server exception for tests")) {
        MavenServerCMDState.setThrowExceptionOnNextServerStart()
        createAndImportProject()
        assertEvent { it.message.contains("Maven server exception for tests") }
      }
    }
    finally {
      MavenServerCMDState.resetThrowExceptionOnNextServerStart()
    }
  }

  @Test
  fun `test maven server not started - bad vm config`() {
    LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("java.util.concurrent.ExecutionException:")) {
      createProjectSubFile(".mvn/jvm.config", "-Xms100m -Xmx10m")
      createAndImportProject()
      assertEvent { it.message.contains("Error occurred during initialization of VM") }
    }
  }

  @Test
  fun `test maven import - bad maven config`() {
    assumeVersionMoreThan("3.3.1");
    createProjectSubFile(".mvn/maven.config", "-aaaaT1")
    createAndImportProject()
    assertModules("test")
    assertEvent { it.message.contains("Unable to parse maven.config:") }
  }

  private fun loggedErrorProcessor(search: String) = object : LoggedErrorProcessor() {
    override fun processError(category: String, message: String?, t: Throwable?, details: Array<out String>): Boolean {
      if (message != null && message.contains(search)) {
        return false
      }
      return true
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