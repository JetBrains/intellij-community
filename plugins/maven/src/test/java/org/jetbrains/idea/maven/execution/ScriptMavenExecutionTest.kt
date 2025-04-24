// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.toCanonicalPath
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.Test

class ScriptMavenExecutionTest : MavenExecutionTest() {


  override fun setUp() {
    super.setUp()
    toggleScriptsRegistryKey(true)
  }


  @Test
  fun testShouldExecuteMavenWrapperScript() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )
    createProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()))
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))
  }


  @Test
  fun testShouldExecuteBundledMavenForAdditionalLinkedProjectIfThereIsNoWrapper() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )
    val anotherLinkedProject = createProjectSubFile("../projectA/pom.xml", createPomXml(
      """
         <groupId>test</groupId>
         <artifactId>projectA</artifactId>
         <version>1</version>
        """.trimIndent()
    ))
    projectsManager.addManagedFiles(listOf(anotherLinkedProject))
    createProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val path = MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toString()
    val executionInfo = execute(MavenRunnerParameters(true, anotherLinkedProject.parent.path, null as String?, mutableListOf("verify"), emptyList()))
    assertTrue("Should run bundled maven ($path) in this case, but command line was: ${executionInfo.system}",
               executionInfo.system.contains(if (SystemInfo.isWindows) "\\bin\\mvn.cmd" else "/bin/mvn"))
  }

  private fun createProjectWrapper() {
    createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=http://example.com")
    if (SystemInfo.isWindows) {
      createProjectSubFile("mvnw.cmd", "@echo $wrapperOutput\r\n@echo %*\r\n")
    }
    else {
      createProjectSubFile("mvnw", "#!/bin/sh\necho $wrapperOutput\n echo $@")
    }
  }

  @Test
  fun testShouldExecuteMavenWrapperForChildProject() = runBlocking {
    createModulePom("m1",
                    """
                      <parent>
                          <groupId>test</groupId>
                          <artifactId>project<</artifactId>
                          <version>1</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      """.trimIndent())
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         <modules>
            <module>m1</module>
         </modules>
         """
    )
    createProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(MavenRunnerParameters(true, projectPath.resolve("m1").toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()))
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))

  }


  companion object {
    const val wrapperOutput = "WRAPPER REPLACEMENT in Intellij tests"
  }
}