// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.SuccessResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.replaceService
import com.intellij.util.io.copyRecursively
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomNioRepositoryHelper
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class RealMavenExecutionTest : MavenExecutionTest() {
  private val myEvents: MutableList<BuildEvent> = ArrayList()
  private lateinit var myDisposable: Disposable

  override fun setUp() {
    super.setUp()
    myDisposable = Disposer.newDisposable(testRootDisposable)
    project.service<ExternalSystemRunConfigurationViewManager>().addListener(object : BuildProgressListener {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        myEvents.add(event)
      }
    }, myDisposable)
  }

  override fun tearDown() {
    try {
      if (::myDisposable.isInitialized) {
        Disposer.dispose(myDisposable)
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }


  @Test
  fun testProjectWithVmOptionsWithoutVMOptions() = runBlocking {
    useProject("mavenProjectWithVmOptionsInEnforcer")
    val executionInfo = execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("compile"), emptyList()))
    assertExecFailed()
  }

  @Test
  fun testProjectWithVmOptionsWithVMOptions() = runBlocking {
    useProject("mavenProjectWithVmOptionsInEnforcer")
    val executionInfo = execute(
      MavenRunnerParameters(true,
                            projectPath.toCanonicalPath(), null as String?,
                            mutableListOf("compile"),
                            emptyList()),
      MavenRunnerSettings().also {
        it.setVmOptions("-Dtest.vmOption=enabled");
      })
    assertExecSucceed(executionInfo)
    assertClassCompiled("org.jetbrains.Main")
  }


  @Test
  fun testProjectWithMavenWrapper() = runBlocking {
    useProject("mavenWithWrapper")
    MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(
      MavenRunnerParameters(true,
                            projectPath.toCanonicalPath(), null as String?,
                            mutableListOf("compile"),
                            emptyList()))
    assertExecSucceed(executionInfo)
    assertClassCompiled("org.example.Main")
  }


  private fun assertClassCompiled(className: String) {
    val target = projectPath.resolve("target")
    assertTrue("No target directory was found", Files.isDirectory(target))
    val classFile = target.resolve("classes").resolve(className.replace('.', '/') + ".class")
    assertTrue("Compiled class $classFile was not found", classFile.isRegularFile())

  }

  private fun assertExecFailed() {
    val finishEvents = myEvents.filterIsInstance<FinishBuildEvent>()
    assertTrue("Expected 1 finish event, got ${finishEvents.size}", finishEvents.size == 1)
    assertTrue(finishEvents[0].result is FailureResult)
  }

  private fun assertExecSucceed(executionInfo: ExecutionInfo) {
    val finishEvents = myEvents.filterIsInstance<FinishBuildEvent>()
    assertTrue("Expected 1 finish event, got ${finishEvents.size}", finishEvents.size == 1)
    assertTrue("", finishEvents[0].result is SuccessResult)
  }


  private fun useProject(name: String) {
    val projectsDataDir: Path = MavenCustomNioRepositoryHelper.originalTestDataPath.resolve("projects")
    val templateProjectDir = projectsDataDir.resolve(name)
    assertTrue("cannot find test project $name in $templateProjectDir", Files.isDirectory(templateProjectDir))

    Files.newDirectoryStream(templateProjectDir).use { stream ->
      stream.forEach { n ->
        n.copyRecursively(projectPath.resolve(templateProjectDir.relativize(n)))
      }
    }
  }
}