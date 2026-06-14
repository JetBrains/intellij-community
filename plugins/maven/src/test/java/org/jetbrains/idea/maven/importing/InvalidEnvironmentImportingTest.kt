// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeVersionMoreThan
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.doImportProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerCMDState
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class InvalidEnvironmentImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var myTestSyncViewManager: SyncViewManager
  private val myEvents: MutableList<BuildEvent> = ArrayList()

  public @BeforeEach
  fun setUp() {
    myTestSyncViewManager = object : SyncViewManager(maven.project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        myEvents.add(event)
      }
    }
    maven.project.replaceService(SyncViewManager::class.java, myTestSyncViewManager, maven.testRootDisposable)
  }

  @Test
  fun testShouldShowWarningIfProjectJDKIsNullAndRollbackToInternal() = runBlocking {
    val projectSdk = ProjectRootManager.getInstance(maven.project).projectSdk
    val jdkForImporter = MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.importingSettings.jdkForImporter
    try {
      LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("Project JDK is not specifie")) {
        MavenWorkspaceSettingsComponent.getInstance(maven.project)
          .settings.getImportingSettings().jdkForImporter = MavenRunnerSettings.USE_PROJECT_JDK
        WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(maven.project).projectSdk = null }
        createAndImportProject()
        val connectors = MavenServerManager.getInstance().getAllConnectors().filter { it.project == maven.project }
        assertNotEmpty(connectors)
        assertEquals(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk(), connectors[0].jdk)
      }
    }
    finally {
      WriteAction.runAndWait<Throwable> { ProjectRootManager.getInstance(maven.project).projectSdk = projectSdk }
      MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.importingSettings.jdkForImporter = jdkForImporter
    }
  }

  @Test
  fun testShouldShowLogsOfMavenServerIfNotStarted() = runBlocking {
    LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("Maven server exception for tests")) {
      MavenServerCMDState.withThrowExceptionOnServerStart {
        createAndImportProject()
        assertEvent { it.message.contains("Maven server exception for tests") }
      }
    }
  }

  @Test
  fun `test maven server not started - bad vm config`() = runBlocking {
    LoggedErrorProcessor.executeWith<RuntimeException>(loggedErrorProcessor("java.util.concurrent.ExecutionException:")) {
      maven.createProjectSubFile(".mvn/jvm.config", "-Xms100m -Xmx10m")
      createAndImportProject()
      assertEvent { it.message.contains("Error occurred during initialization of VM") }
    }
  }

  @Test
  fun `test maven import - bad maven config`() = runBlocking {
    maven.assumeVersionMoreThan("3.3.1")
    maven.createProjectSubFile(".mvn/maven.config", "-aaaaT1")
    createAndImportProject()
    maven.assertModules("test")
    assertEvent { it.message.contains("Unrecognized option: -aaaaT1") || it.message.contains("Unable to parse maven.config") }
  }

  @Test
  fun `test maven sync with old JDK`() = runBlocking {
    maven.assumeMaven4()
    val sdk = createTestSdk11()
    edtWriteAction {
      ProjectJdkTable.getInstance(maven.project).addJdk(sdk, maven.testRootDisposable)
      ProjectRootManager.getInstance(maven.project).setProjectSdk(sdk);
    }
    try {
      // If you want to set it as the project SDK
      createAndImportProject()
      maven.assertModules("test")
      assertEvent { it.message.contains("Maven JDK Version for Importer Is Too Low") }
    }
    finally {
      edtWriteAction {
        ProjectJdkTable.getInstance(maven.project).removeJdk(sdk)
        ProjectRootManager.getInstance(maven.project).setProjectSdk(null)
      }
    }

  }

  private suspend fun createTestSdk11(): Sdk {
    val sdk: Sdk = ProjectJdkTable.getInstance(maven.project).createSdk("test-jdk11", JavaSdk.getInstance())
    val sdkModificator = sdk.sdkModificator
    sdkModificator.homePath = "jdk11-home-path"
    sdkModificator.versionString = "11"
    edtWriteAction { sdkModificator.commitChanges() }
    return sdk
  }


  private fun loggedErrorProcessor(search: String) = object : LoggedErrorProcessor() {
    override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> =
      if (message.contains(search)) Action.NONE else Action.ALL
  }

  private fun assertEvent(description: String = "Asserted", predicate: (BuildEvent) -> Boolean) {
    if (myEvents.isEmpty()) {
      Assertions.fail<Any>("Message \"${description}\" was not found. No messages was recorded at all")
    }
    if (myEvents.any(predicate)) {
      return
    }

    Assertions.fail<Any>("Message \"${description}\" was not found. Known messages:\n" +
                    myEvents.joinToString("\n") { "${it}" })
  }

  private fun createAndImportProject() {
    maven.createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>test</artifactId>" +
                     "<version>1.0</version>")
    runBlockingMaybeCancellable {
      maven.doImportProjectsAsync(listOf(maven.projectPom), false)
    }
  }
}
