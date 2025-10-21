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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.io.copyRecursively
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomNioRepositoryHelper
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

class RealMavenExecutionTest : MavenExecutionTest() {
  private val myEvents: MutableList<BuildEvent> = CopyOnWriteArrayList()
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
  fun testExternalExecutor() = runBlocking {
    createProjectSubFile("src/main/java/A.java", "public class A {}")
    createProjectPom("""
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                         """.trimIndent())
    importProjectAsync()
    assertFalse(projectPath.resolve("target").exists())
    execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("compile"), emptyList()))
    assertTrue(projectPath.resolve("target").exists())
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

  @Test
  fun testProjectWithResolveToWorkspace() = runBlocking {
    useProject("maven-modules")
    importProjectAsync()
    val executionInfo = execute(
      MavenRunnerParameters(true,
                            projectPath.toCanonicalPath(), null as String?,
                            mutableListOf("compile"),
                            emptyList()))
    assertExecSucceed(executionInfo)
    assertClassCompiled("m1", "org.example.m1.ClassM1")
    assertClassCompiled("m2", "org.example.m2.ClassM2")

    val failedAppRunningInfo = execute(
      MavenRunnerParameters(true,
                            projectPath.resolve("m1").toCanonicalPath(), null as String?,
                            mutableListOf("exec:java", "-Dexec.mainClass=org.example.m1.ClassM1"),
                            emptyList()))
    assertTrue("build should fail, output was: ${failedAppRunningInfo}",
               failedAppRunningInfo.stdout.contains("BUILD FAILURE", true))


    val succeedAppRunningInfo = execute(
      MavenRunnerParameters(true,
                            projectPath.resolve("m1").toCanonicalPath(), null as String?,
                            mutableListOf("exec:java", "-Dexec.mainClass=org.example.m1.ClassM1"),
                            emptyList()).also { it.isResolveToWorkspace = true })
    assertTrue("build should print output from class, output was: ${succeedAppRunningInfo}",
               succeedAppRunningInfo.stdout.contains("Hello from Module 2", false))
  }

  @Test
  fun testProjectWorkspaceMap() = runBlocking {
    useProject("maven-modules")
    importProjectAsync()
    assertModules("parent", "m1", "m2")
    val map = MavenExternalParameters.getProjectModuleMap(project)
    assertEquals("should contain 7 records", 7, map.size)

    assertArtifactMapping(map, "test:parent:pom:1", "pom.xml")
    assertArtifactMapping(map, "test:m2:pom:1", "m2/pom.xml")
    assertArtifactMapping(map, "test:m1:pom:1", "m1/pom.xml")
    assertArtifactMapping(map, "test:m2:jar:1", "m2/target/classes")
    assertArtifactMapping(map, "test:m1:jar:1", "m1/target/classes")
    assertArtifactMapping(map, "test:m2:test-jar:1", "m2/target/test-classes")
    assertArtifactMapping(map, "test:m1:test-jar:1", "m1/target/test-classes")
  }

  fun assertArtifactMapping(map: Properties, key: String, expected: String) {
    val actual = map.getProperty(key)
    assertNotNull("Workspace mapping should contain $key", key)
    val expectedPath = projectPath.resolve(expected).absolutePathString()
    val actualPath = projectPath.resolve(actual).absolutePathString()
    assertEquals("Path for $key is wrong", expectedPath, actualPath)
  }


  private fun assertClassCompiled(className: String) {
    assertClassCompiled(".", className)
  }

  private fun assertClassCompiled(module: String, className: String) {
    val subPath = projectPath.resolve(module)
    val target = subPath.resolve("target")
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


  private suspend fun useProject(name: String) {
    val projectsDataDir: Path = MavenCustomNioRepositoryHelper.originalTestDataPath.resolve("projects")
    val templateProjectDir = projectsDataDir.resolve(name)

    assertTrue("cannot find test project $name in $templateProjectDir", Files.isDirectory(templateProjectDir))

    MavenLog.LOG.warn("copying from $templateProjectDir to ${projectPath}")
    Files.newDirectoryStream(templateProjectDir).use { stream ->
      stream.forEach { n ->
        MavenLog.LOG.warn("copying $n as ${templateProjectDir.relativize(n)}")
        val target = projectPath.resolve(templateProjectDir.relativize(n))
        n.copyRecursively(target)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target)
      }
    }

    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectPath.resolve("pom.xml"))?.let {
      projectPom = it
    }

    MavenLog.LOG.warn(" dir path = ${projectPath}")
    MavenLog.LOG.warn(" dir exists = ${projectPath.exists()}")
    MavenLog.LOG.warn(" dir content = ${projectPath.listDirectoryEntries()}")

  }
}