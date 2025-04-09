/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.execution

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
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

  private fun execute(params: MavenRunnerParameters) {
    val sema = Semaphore()
    sema.down()
    edt<RuntimeException> {
      MavenRunConfigurationType.runConfiguration(
        project, params, mavenGeneralSettings,
        MavenRunnerSettings(),
        ProgramRunner.Callback { descriptor ->
          descriptor.processHandler!!.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
              println(event.text)
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
  }
}
