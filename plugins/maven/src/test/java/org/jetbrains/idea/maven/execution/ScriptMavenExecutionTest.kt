// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.io.copyRecursively
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenInSpecificPath
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.Assume
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

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
    createFakeProjectWrapper()
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
    createFakeProjectWrapper()
    waitForImportWithinTimeout {
      mavenGeneralSettings.mavenHomeType = MavenWrapper
      val path = MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toString()
      val executionInfo = execute(MavenRunnerParameters(true, anotherLinkedProject.parent.path, null as String?, mutableListOf("verify"), emptyList()))
      assertTrue("Should run bundled maven ($path) in this case, but command line was: ${executionInfo.system}",
                 executionInfo.system.contains(if (SystemInfo.isWindows) "\\bin\\mvn.cmd" else "/bin/mvn"))

    }
  }

  private fun createFakeProjectWrapper() {
    createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=http://example.com")
    if (EelOsFamily.Windows == project.getEelDescriptor().osFamily) {
      createProjectSubFile("mvnw.cmd", "@echo $wrapperOutput\r\n@echo %*\r\n@set")
    }
    else {
      createProjectSubFile("mvnw", "#!/bin/sh\necho $wrapperOutput\necho $@ \nprintenv ")
    }
  }

  @Test
  fun testShouldExecuteBundledMavenIfThereAreSpacesInMavenPath() = runBlocking {
    val target = dir.resolve("maven path with spaces")
    createFakeMaven(target)
    mavenGeneralSettings.mavenHomeType = MavenInSpecificPath(target.toString())
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )

    val executionInfo = execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()), settings = MavenRunnerSettings().also {
      it.setVmOptions("-XABC")
    })
    assertTrue("Should run maven", executionInfo.stdout.contains(mavenOutput))
    shouldContainOption(executionInfo, "-XABC")
  }


  @Test
  fun testShouldExecuteBundledMavenIfThereAreNoSpacesInMavenPath() = runBlocking {
    Assume.assumeFalse("There are spaces in path, doesnt make sence to run the test", dir.toString().contains(" "))
    val target = dir.resolve("maven-dist")
    createFakeMaven(target)
    mavenGeneralSettings.mavenHomeType = MavenInSpecificPath(target.toString())
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )

    val executionInfo = execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()), settings = MavenRunnerSettings().also {
      it.setVmOptions("-XABC")
    })
    assertTrue("Should run maven", executionInfo.stdout.contains(mavenOutput))
    shouldContainOption(executionInfo, "-XABC")
  }

  private fun createFakeMaven(target: Path) {
    MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.copyRecursively(target)
    val binFile = if (SystemInfo.isWindows) {
      target.resolve("bin/mvn.cmd")
    }
    else {
      target.resolve("bin/mvn")
    }
    assertTrue("Cannot create fake maven directory", binFile.isRegularFile())
    if (SystemInfo.isWindows) {
      Files.write(binFile, "@echo $mavenOutput\r\n@echo %*\r\n@set".toByteArray(StandardCharsets.UTF_8))
    }
    else {
      Files.write(binFile, "#!/bin/sh\necho $mavenOutput\necho $@ \nprintenv ".toByteArray(StandardCharsets.UTF_8))
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
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(MavenRunnerParameters(true, projectPath.resolve("m1").toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()))
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))

  }

  @Test
  fun testShouldExecuteMavenScriptWithDebugParameters() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper

    val debugExecInfo = debugMavenRunConfiguration(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("exec:java"), emptyList()))
    assertTrue("Should run wrapper", debugExecInfo.stdout.contains(wrapperOutput))
    val debugOpts = debugExecInfo.stdout.lines().singleOrNull { it.startsWith("MAVEN_OPTS") }
    assertNotNull(debugExecInfo.toString(), debugOpts)
    assertTrue(debugOpts, debugOpts!!.contains("-agentlib:jdwp=transport=dt_socket"))
    // maven.use.scripts.debug.agent = true is set to `true` by default
    assertTrue(debugOpts, debugOpts.contains("debugger-agent.jar"))
  }


  @Test
  fun testShouldExecuteMavenScriptWithEnvVariablesInRunConfiguration() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(params = MavenRunnerParameters(
      true, projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.environmentProperties = mapOf("FOOOOO" to "BAAAAAAR")
                                })
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))
    assertTrue("Should pass env variables in run configuration  but stdout: ${executionInfo.stdout}", executionInfo.stdout.contains("FOOOOO=BAAAAAAR"))

  }

  @Test
  fun testShouldUseExistingEncodingIfDefinedInMavenOpts() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(params = MavenRunnerParameters(
      true, projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.environmentProperties = mapOf("MAVEN_OPTS" to "-Dfile.encoding=CP866")
                                })
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))
    assertTrue("Should pass env variables in run configuration  but stdout: ${executionInfo.stdout}", executionInfo.stdout.contains("MAVEN_OPTS=-Dfile.encoding=CP866"))

  }

  @Test
  fun testShouldUseExistingEncodingIfDefinedInJavaToolsOptions() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(params = MavenRunnerParameters(
      true, projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.environmentProperties = mapOf("JAVA_TOOLS_OPTIONS" to "-Dfile.encoding=CP866")
                                })
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))
    assertTrue("Should pass env variables in run configuration  but stdout: ${executionInfo.stdout}", executionInfo.stdout.contains("JAVA_TOOLS_OPTIONS=-Dfile.encoding=CP866"))

  }

  @Test
  fun testShouldExecuteMavenScriptWithPomFile() = runBlocking {

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
         <packaging>pom</packaging>
         <modules>
            <module>m1</module>
         </modules>
         """
    )

    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), "m1", mutableListOf("verify"), emptyList()))
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))
    assertTrue("Should run build for specified pom but system: ${executionInfo.system}", executionInfo.system.contains("-f m1"))

  }

  @Test
  fun testShouldExecuteMavenScriptWithVmOptions() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = execute(params = MavenRunnerParameters(
      true, projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.setVmOptions("-XMyJavaParameter")
                                })
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))
    shouldContainOption(executionInfo, "-XMyJavaParameter")
  }

  private fun shouldContainOption(executionInfo: ExecutionInfo, option: String) {
    val mavenOptsLineStarts = executionInfo.stdout.indexOf("MAVEN_OPTS=")
    assertTrue("Should pass env variables in run configuration, but stdout: ${executionInfo.stdout}", mavenOptsLineStarts != -1)
    val mavenOptsLineEnd = executionInfo.stdout.indexOf("\n", mavenOptsLineStarts)
    val mavenOptsLine = executionInfo.stdout.substring(mavenOptsLineStarts, mavenOptsLineEnd)
    assertTrue("MAVEN_OPTS should contain parameters, but was ${mavenOptsLine}", mavenOptsLine.contains(option))
  }

  @Test
  fun testShouldExecuteMavenScriptWithLocalCache() = runBlocking {
    importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    mavenGeneralSettings.mavenHomeType = MavenWrapper
    val localCache = if (project.getEelDescriptor().osFamily == EelOsFamily.Windows) {
      "c:\\my\\Path\\To\\Local\\Repository"
    }
    else {
      "/my/Path/To/Local/Repository"
    }
    val executionInfo = execute(params = MavenRunnerParameters(
      true, projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                generalSettings = mavenGeneralSettings.clone().also {
                                  it.setLocalRepository(localCache)
                                }
    )
    assertTrue("Should run wrapper", executionInfo.stdout.contains(wrapperOutput))
    assertTrue("Should proper pass local repository: ${executionInfo.system}", executionInfo.system.contains(" -Dmaven.repo.local=$localCache "))

  }
  
  companion object {
    const val wrapperOutput = "WRAPPER REPLACEMENT in Intellij tests"
    const val mavenOutput = "MAVEN REPLACEMENT in Intellij tests"
  }
}