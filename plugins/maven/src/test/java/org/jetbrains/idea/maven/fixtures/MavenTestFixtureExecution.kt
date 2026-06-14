// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.RunManagerImpl.Companion.getInstanceImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.assertExcludes
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertResources
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.createStdProjectFolders
import com.intellij.maven.testFramework.fixtures.defaultResources
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.junit.Assert.fail
import java.nio.charset.Charset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// Ported from MavenExecutionTest (and its base MavenExecutionTestCase JDK lifecycle stays per-leaf).

data class ExecutionInfo(val system: String, val stdout: String, val stderr: String, val charset: Charset?)

class MyTestExecutionListener(
  val stdout: StringBuilder = StringBuilder(),
  val stderr: StringBuilder = StringBuilder(),
  val system: StringBuilder = StringBuilder(),
  val semaphore: Semaphore,
  val descriptor: RunContentDescriptor,
) : ProcessListener {

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    if (outputType !is ProcessOutputType) {
      fail("output type is wrong")
    }
    else {
      if (outputType.isStdout)
        stdout.append(event.text)
      else if (outputType.isStderr)
        stderr.append(event.text)
      else
        system.append(event.text)
    }
  }

  override fun processTerminated(event: ProcessEvent) {
    semaphore.up()
    runInEdtAndWait {
      Disposer.dispose(descriptor)
    }
  }
}

fun MavenImportingTestFixture.toggleScriptsRegistryKey(useScripts: Boolean) {
  Registry.get("maven.use.scripts").setValue(useScripts, testRootDisposable)
}

fun MavenImportingTestFixture.execute(
  params: MavenRunnerParameters,
  settings: MavenRunnerSettings = MavenRunnerSettings(),
  generalSettings: MavenGeneralSettings = mavenGeneralSettings,
  maxTimeToWait: Duration = 1.minutes,
): ExecutionInfo {
  val sema = Semaphore()
  val stdout = StringBuilder()
  val stderr = StringBuilder()
  val system = StringBuilder()
  var charset: Charset? = null
  sema.down()
  runInEdtAndWait {
    MavenRunConfigurationType.runConfiguration(
      project, params, generalSettings,
      settings,
      ProgramRunner.Callback { descriptor ->
        descriptor.processHandler!!.addProcessListener(MyTestExecutionListener(stdout, stderr, system, sema, descriptor))
        charset = (descriptor.processHandler as? BaseProcessHandler<*>)?.charset
      }, false)
  }
  sema.waitFor(maxTimeToWait.inWholeMilliseconds)
  return ExecutionInfo(system.toString(), stdout.toString(), stderr.toString(), charset)
}

fun MavenImportingTestFixture.debugMavenRunConfiguration(parameters: MavenRunnerParameters, maxTimeToWait: Duration = 1.minutes): ExecutionInfo {
  val runManager = getInstanceImpl(project)
  val mavenTemplateConfiguration = MavenRunConfigurationType.getInstance().configurationFactories[0].createTemplateConfiguration(
    project)
  val mavenConfiguration = MavenRunConfigurationType.getInstance().configurationFactories[0].createConfiguration("myConfiguration",
                                                                                                                 mavenTemplateConfiguration)
  (mavenConfiguration as MavenRunConfiguration).runnerParameters = parameters

  val configuration = RunnerAndConfigurationSettingsImpl(runManager, mavenConfiguration)

  val sema = Semaphore()
  val stdout = StringBuilder()
  val stderr = StringBuilder()
  val system = StringBuilder()
  sema.down()
  var charset: Charset? = null

  ExecutionUtil.doRunConfiguration(configuration, DefaultDebugExecutor.getDebugExecutorInstance(), null, null, null) { environment ->
    environment.callback = ProgramRunner.Callback { descriptor ->
      descriptor.processHandler!!.addProcessListener(MyTestExecutionListener(stdout, stderr, system, sema, descriptor))
      charset = (descriptor.processHandler as? BaseProcessHandler<*>)?.charset
    }
  }
  sema.waitFor(maxTimeToWait.inWholeMilliseconds)
  return ExecutionInfo(system.toString(), stdout.toString(), stderr.toString(), charset)
}

// Body of the test inherited from MavenExecutionTest, exposed as a shared helper so each leaf can run it via a thin @Test.
suspend fun MavenImportingTestFixture.checkUpdatingExcludedFoldersAfterExecution() {
  createStdProjectFolders()
  createProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  importProjectAsync()
  createProjectSubDirs("target/generated-sources/foo", "target/bar")

  assertModules("project")
  assertExcludes("project", "target")

  val params = MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("compile"), emptyList())
  execute(params)

  assertSources("project", "src/main/java")
  assertResources("project", *defaultResources())

  assertExcludes("project", "target")
}
