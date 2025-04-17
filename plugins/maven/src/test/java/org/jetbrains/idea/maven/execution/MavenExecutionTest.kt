// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ProgramRunner
import com.intellij.maven.testFramework.MavenExecutionTestCase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.io.path.exists

abstract class MavenExecutionTest : MavenExecutionTestCase() {

  protected fun toggleScriptsRegistryKey(useScripts: Boolean) {
    Registry.get("maven.use.scripts").setValue(useScripts, testRootDisposable)
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
  fun testUpdatingExcludedFoldersAfterExecution() = runBlocking {
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


  protected fun execute(params: MavenRunnerParameters): ExecutionInfo {
    val sema = Semaphore()
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val system = StringBuilder()
    sema.down()
    edt<RuntimeException> {
      MavenRunConfigurationType.runConfiguration(
        project, params, mavenGeneralSettings,
        MavenRunnerSettings(),
        ProgramRunner.Callback { descriptor ->
          descriptor.processHandler!!.addProcessListener(object : ProcessListener {
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
              sema.up()
              edt<RuntimeException> {
                Disposer.dispose(descriptor)
              }
            }
          })
        }, false)
    }
    sema.waitFor()
    return ExecutionInfo(system.toString(), stdout.toString(), stderr.toString())
  }
}

data class ExecutionInfo(val system: String, val stdout: String, val stderr: String)
