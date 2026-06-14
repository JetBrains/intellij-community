// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.SuccessResult
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeVersionAtLeast
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.copyRecursively
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomNioRepositoryHelper
import org.jetbrains.idea.maven.fixtures.ExecutionInfo
import org.jetbrains.idea.maven.fixtures.checkUpdatingExcludedFoldersAfterExecution
import org.jetbrains.idea.maven.fixtures.execute
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class RealMavenExecutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private val myEvents: MutableList<BuildEvent> = CopyOnWriteArrayList()
  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenExecutionTestJDK")
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.setUp() })
    })
    maven.project.service<ExternalSystemRunConfigurationViewManager>().addListener(object : BuildProgressListener {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        myEvents.add(event)
      }
    }, maven.testRootDisposable)
  }

  @AfterEach
  fun tearDownJdk() {
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.tearDown() })
    })
  }

  @Test
  fun testUpdatingExcludedFoldersAfterExecution() = runBlocking {
    maven.checkUpdatingExcludedFoldersAfterExecution()
  }

  @Test
  fun testExternalExecutor() = runBlocking {
    maven.createProjectSubFile("src/main/java/A.java", "public class A {}")
    maven.createProjectPom("""
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                         """.trimIndent())
    maven.importProjectAsync()
    assertFalse(maven.projectPath.resolve("target").exists())
    maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("compile"), emptyList()))
    assertTrue(maven.projectPath.resolve("target").exists())
  }


  @Test
  fun testProjectWithVmOptionsWithoutVMOptions() = runBlocking {
    useProject("mavenProjectWithVmOptionsInEnforcer")
    maven.importProjectAsync()
    val executionInfo = maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("compile"), emptyList()))
    assertExecFailed()
  }

  @Test
  fun testProjectWithVmOptionsWithVMOptions() = runBlocking {
    maven.assumeVersionAtLeast("3.6.3")
    useProject("mavenProjectWithVmOptionsInEnforcer")
    maven.importProjectAsync()
    val executionInfo = maven.execute(
      MavenRunnerParameters(true,
                            maven.projectPath.toCanonicalPath(), null as String?,
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
    maven.importProjectAsync()
    MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.generalSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(
      MavenRunnerParameters(true,
                            maven.projectPath.toCanonicalPath(), null as String?,
                            mutableListOf("compile"),
                            emptyList()))
    assertExecSucceed(executionInfo)
    assertClassCompiled("org.example.Main")
  }

  @Test
  fun testProjectWithResolveToWorkspace() = runBlocking {
    useProject("maven-modules")
    maven.importProjectAsync()
    val executionInfo = maven.execute(
      MavenRunnerParameters(true,
                            maven.projectPath.toCanonicalPath(), null as String?,
                            mutableListOf("compile"),
                            emptyList()))
    assertExecSucceed(executionInfo)
    assertClassCompiled("m1", "org.example.m1.ClassM1")
    assertClassCompiled("m2", "org.example.m2.ClassM2")

    val failedAppRunningInfo = maven.execute(
      MavenRunnerParameters(true,
                            maven.projectPath.resolve("m1").toCanonicalPath(), null as String?,
                            mutableListOf("exec:java", "-Dexec.mainClass=org.example.m1.ClassM1"),
                            emptyList()))
    assertTrue(failedAppRunningInfo.stdout.contains("BUILD FAILURE", true), "build should fail, output was: ${failedAppRunningInfo}")


    val succeedAppRunningInfo = maven.execute(
      MavenRunnerParameters(true,
                            maven.projectPath.resolve("m1").toCanonicalPath(), null as String?,
                            mutableListOf("exec:java", "-Dexec.mainClass=org.example.m1.ClassM1"),
                            emptyList()).also { it.isResolveToWorkspace = true })
    assertTrue(succeedAppRunningInfo.stdout.contains("Hello from Module 2", false), "build should print output from class, output was: ${succeedAppRunningInfo}")
  }

  @Test
  fun testProjectWorkspaceMap() = runBlocking {
    useProject("maven-modules")
    maven.importProjectAsync()
    maven.assertModules("parent", "m1", "m2")
    val map = MavenExternalParameters.getProjectModuleMap(maven.project)
    assertEquals(7, map.size, "should contain 7 records")

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
    assertNotNull(key, "Workspace mapping should contain $key")
    val expectedPath = maven.projectPath.resolve(expected).absolutePathString()
    val actualPath = maven.projectPath.resolve(actual).absolutePathString()
    assertEquals(expectedPath, actualPath, "Path for $key is wrong")
  }


  private fun assertClassCompiled(className: String) {
    assertClassCompiled(".", className)
  }

  private fun assertClassCompiled(module: String, className: String) {
    val subPath = maven.projectPath.resolve(module)
    val target = subPath.resolve("target")
    assertTrue(Files.isDirectory(target), "No target directory was found")
    val classFile = target.resolve("classes").resolve(className.replace('.', '/') + ".class")
    assertTrue(classFile.isRegularFile(), "Compiled class $classFile was not found")
  }

  private fun assertExecFailed() {
    val finishEvents = myEvents.filterIsInstance<FinishBuildEvent>()
    assertTrue(finishEvents.size == 1, "Expected 1 finish event, got ${finishEvents.size}")
    assertTrue(finishEvents[0].result is FailureResult)
  }

  private fun assertExecSucceed(executionInfo: ExecutionInfo) {
    val finishEvents = myEvents.filterIsInstance<FinishBuildEvent>()
    assertTrue(finishEvents.size == 1, "Expected 1 finish event, got ${finishEvents.size}")
    assertTrue(finishEvents[0].result is SuccessResult, "")
  }


  private suspend fun useProject(name: String) {
    val projectsDataDir: Path = MavenCustomNioRepositoryHelper.originalTestDataPath.resolve("projects")
    val templateProjectDir = projectsDataDir.resolve(name)

    assertTrue(Files.isDirectory(templateProjectDir), "cannot find test project $name in $templateProjectDir")

    MavenLog.LOG.warn("copying from $templateProjectDir to ${maven.projectPath}")
    Files.newDirectoryStream(templateProjectDir).use { stream ->
      stream.forEach { n ->
        MavenLog.LOG.warn("copying $n as ${templateProjectDir.relativize(n)}")
        val target = maven.projectPath.resolve(templateProjectDir.relativize(n))
        n.copyRecursively(target)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target)
      }
    }

    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(maven.projectPath.resolve("pom.xml"))?.let {
      maven.projectPom = it
    }

    MavenLog.LOG.warn(" dir path = ${maven.projectPath}")
    MavenLog.LOG.warn(" dir exists = ${maven.projectPath.exists()}")
    MavenLog.LOG.warn(" dir content = ${maven.projectPath.listDirectoryEntries()}")

  }
}