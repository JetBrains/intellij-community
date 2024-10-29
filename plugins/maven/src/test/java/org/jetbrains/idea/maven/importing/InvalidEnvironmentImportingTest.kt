// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.replaceService
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
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
    myTestSyncViewManager = object : SyncViewManager(project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        myEvents.add(event)
      }
    }
    project.replaceService(SyncViewManager::class.java, myTestSyncViewManager, testRootDisposable)
  }

  @Test
  fun testShouldShowWarningIfProjectJDKIsNullAndRollbackToInternal() = runBlocking {
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    val jdkForImporter = MavenWorkspaceSettingsComponent.getInstance(project).settings.importingSettings.jdkForImporter
    try {
      LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("Project JDK is not specifie")) {
        MavenWorkspaceSettingsComponent.getInstance(project)
          .settings.getImportingSettings().jdkForImporter = MavenRunnerSettings.USE_PROJECT_JDK
        WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(project).projectSdk = null }
        createAndImportProject()
        val connectors = MavenServerManager.getInstance().getAllConnectors().filter { it.project == project }
        assertNotEmpty(connectors)
        TestCase.assertEquals(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk(), connectors[0].jdk)
      }
    }
    finally {
      WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(project).projectSdk = projectSdk }
      MavenWorkspaceSettingsComponent.getInstance(project).settings.importingSettings.jdkForImporter = jdkForImporter
    }
  }

  @Test
  fun testShouldShowLogsOfMavenServerIfNotStarted() = runBlocking {
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
  fun `test maven server not started - bad vm config`() = runBlocking {
    LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("java.util.concurrent.ExecutionException:")) {
      createProjectSubFile(".mvn/jvm.config", "-Xms100m -Xmx10m")
      createAndImportProject()
      assertEvent { it.message.contains("Error occurred during initialization of VM") }
    }
  }

  @Test
  fun `test maven import - bad maven config`() = runBlocking {
    needFixForMaven4()
    assumeVersionMoreThan("3.3.1")
    createProjectSubFile(".mvn/maven.config", "-aaaaT1")
    createAndImportProject()
    assertModules("test")
    assertEvent { it.message.contains("Unrecognized option: -aaaaT1") }
  }

  private fun loggedErrorProcessor(search: String) = object : LoggedErrorProcessor() {
    override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> =
      if (message.contains(search)) Action.NONE else Action.ALL
  }

  private fun assertEvent(description: String = "Asserted", predicate: (BuildEvent) -> Boolean) {
    if (myEvents.isEmpty()) {
      fail("Message \"${description}\" was not found. No messages was recorded at all")
    }
    if (myEvents.any(predicate)) {
      return
    }

    fail("Message \"${description}\" was not found. Known messages:\n" +
         myEvents.joinToString("\n") { "${it}" })
  }

  private fun createAndImportProject() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>test</artifactId>" +
                     "<version>1.0</version>")
    runBlockingMaybeCancellable {
      doImportProjectsAsync(listOf(projectPom), false)
    }
  }
}
