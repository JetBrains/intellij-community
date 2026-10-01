// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ProgramRunner
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A delegated build reports to the Build tool window. Its start event must carry the rerun action,
 * as a JPS build does, so the user can repeat the build from the tool window.
 *
 * The test application registers a dummy [BuildViewManager] that drops every event,
 * so the test installs a manager that records the start events.
 */
@TestApplication
class MavenDelegateBuildRerunActionTest {

  private val maven by mavenImportingFixture()
  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenDelegateBuildRerunActionTestJDK")
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.setUp() }
    }
  }

  @AfterEach
  fun tearDownJdk() {
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.tearDown() }
    }
  }

  @Test
  fun `a delegated build offers a rerun action in the build tool window`() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      """.trimIndent())

    val startEvents = CopyOnWriteArrayList<StartBuildEvent>()
    val recordingViewManager = object : BuildViewManager(maven.project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        if (event is StartBuildEvent) startEvents.add(event)
      }
    }
    maven.project.replaceService(BuildViewManager::class.java, recordingViewManager, maven.testRootDisposable)

    val finished = CountDownLatch(1)
    val parameters = MavenRunnerParameters(true, maven.projectPath.toString(), "pom.xml", listOf("validate"), emptyList(), emptyList())
    runInEdtAndWait {
      MavenRunConfigurationType.runConfiguration(maven.project, parameters, null, null, ProgramRunner.Callback { descriptor ->
        descriptor.processHandler!!.addProcessListener(object : ProcessListener {
          override fun processTerminated(event: ProcessEvent) = finished.countDown()
        })
      }, true)
    }
    assertTrue(finished.await(2, TimeUnit.MINUTES), "The delegated Maven build did not finish")

    val startEvent = startEvents.firstOrNull() ?: fail("The Build tool window received no start event")
    val restartActions = (startEvent.buildDescriptor as DefaultBuildDescriptor).restartActions
    assertTrue(restartActions.any { it is MavenRebuildAction },
               "Expected a rerun action in the Build tool window, got ${restartActions.map { it.javaClass.simpleName }}")
  }
}
