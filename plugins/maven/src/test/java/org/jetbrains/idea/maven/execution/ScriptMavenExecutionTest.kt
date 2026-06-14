// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.idea.TestFor
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.ExtensionTestUtil.addExtensions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.copyRecursively
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.run.MAVEN_EXECUTION_CONFIGURATOR
import org.jetbrains.idea.maven.execution.run.MavenExecutionConfigurator
import org.jetbrains.idea.maven.execution.run.MavenExecutionConfiguratorProvider
import org.jetbrains.idea.maven.fixtures.ExecutionInfo
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.checkUpdatingExcludedFoldersAfterExecution
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import org.jetbrains.idea.maven.fixtures.debugMavenRunConfiguration
import org.jetbrains.idea.maven.fixtures.execute
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import org.jetbrains.idea.maven.fixtures.toggleScriptsRegistryKey
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.project.MavenInSpecificPath
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ScriptMavenExecutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  


  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenExecutionTestJDK")
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.setUp() })
    })
    maven.toggleScriptsRegistryKey(true)
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
  fun testShouldExecuteMavenWrapperScript() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()))
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
  }


  @Test
  fun testShouldExecuteBundledMavenForAdditionalLinkedProjectIfThereIsNoWrapper() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )
    val anotherLinkedProject = maven.createProjectSubFile("../projectA/pom.xml", maven.createPomXml(
      """
         <groupId>test</groupId>
         <artifactId>projectA</artifactId>
         <version>1</version>
        """.trimIndent()
    ))
    maven.projectsManager.addManagedFiles(listOf(anotherLinkedProject))
    createFakeProjectWrapper()
    maven.waitForImportWithinTimeout {
      maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
      val path = MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toString()
      val executionInfo =
        maven.execute(MavenRunnerParameters(true, anotherLinkedProject.parent.path, null as String?, mutableListOf("verify"), emptyList()))
      assertTrue(executionInfo.system.contains(if (SystemInfo.isWindows) "\\bin\\mvn.cmd" else "/bin/mvn"), "Should run bundled maven ($path) in this case, but command line was: ${executionInfo.system}")

    }
  }

  private fun createFakeProjectWrapper() {
    maven.createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=http://example.com")
    if (EelOsFamily.Windows == maven.project.getEelDescriptor().osFamily) {
      maven.createProjectSubFile("mvnw.cmd", "@echo $wrapperOutput\r\n@echo %*\r\n@set")
    }
    else {
      maven.createProjectSubFile("mvnw", "#!/bin/sh\necho $wrapperOutput\necho $@ \nprintenv ")
    }
  }

  @Test
  fun testShouldExecuteBundledMavenIfThereAreSpacesInMavenPath() = runBlocking {
    val target = maven.dir.resolve("maven path with spaces")
    createFakeMaven(target)
    maven.mavenGeneralSettings.mavenHomeType = MavenInSpecificPath(target.toString())
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )

    val executionInfo =
      maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()),
              settings = MavenRunnerSettings().also {
                it.setVmOptions("-XABC")
              })
    assertTrue(executionInfo.stdout.contains(mavenOutput), "Should run maven")
    shouldContainOption(executionInfo, "-XABC")
  }


  @Test
  fun testShouldExecuteBundledMavenIfThereAreNoSpacesInMavenPath() = runBlocking {
    Assumptions.assumeFalse(maven.dir.toString().contains(" "), "There are spaces in path, doesnt make sence to run the test")
    val target = maven.dir.resolve("maven-dist")
    createFakeMaven(target)
    maven.mavenGeneralSettings.mavenHomeType = MavenInSpecificPath(target.toString())
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>"""
    )

    val executionInfo =
      maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("verify"), emptyList()),
              settings = MavenRunnerSettings().also {
                it.setVmOptions("-XABC")
              })
    assertTrue(executionInfo.stdout.contains(mavenOutput), "Should run maven")
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
    assertTrue(binFile.isRegularFile(), "Cannot create fake maven directory")
    if (SystemInfo.isWindows) {
      Files.write(binFile, "@echo $mavenOutput\r\n@echo %*\r\n@set".toByteArray(StandardCharsets.UTF_8))
    }
    else {
      Files.write(binFile, "#!/bin/sh\necho $mavenOutput\necho $@ \nprintenv ".toByteArray(StandardCharsets.UTF_8))
    }
  }


  @Test
  fun testShouldExecuteMavenWrapperForChildProject() = runBlocking {
    maven.createModulePom("m1",
                    """
                      <parent>
                          <groupId>test</groupId>
                          <artifactId>project<</artifactId>
                          <version>1</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      """.trimIndent())
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         <modules>
            <module>m1</module>
         </modules>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(MavenRunnerParameters(true,
                                                      maven.projectPath.resolve("m1").toCanonicalPath(),
                                                      null as String?,
                                                      mutableListOf("verify"),
                                                      emptyList()))
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")

  }

  @Test
  fun testShouldExecuteMavenScriptWithDebugParameters() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper

    val debugExecInfo = maven.debugMavenRunConfiguration(MavenRunnerParameters(true,
                                                                         maven.projectPath.toCanonicalPath(),
                                                                         null as String?,
                                                                         mutableListOf("exec:java"),
                                                                         emptyList()))
    assertTrue(debugExecInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    val debugOpts = debugExecInfo.stdout.lines().singleOrNull { it.startsWith("MAVEN_OPTS") }
    assertNotNull(debugOpts, debugExecInfo.toString())
    assertTrue(debugOpts!!.contains("-agentlib:jdwp=transport=dt_socket"), debugOpts)
    // maven.use.scripts.debug.agent = true is set to `true` by default
    assertTrue(debugOpts.contains("debugger-agent.jar"), debugOpts)
  }


  @Test
  fun testShouldExecuteMavenScriptWithEnvVariablesInRunConfiguration() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(params = MavenRunnerParameters(
      true, maven.projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.environmentProperties = mapOf("FOOOOO" to "BAAAAAAR")
                                })
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    assertTrue(executionInfo.stdout.contains("FOOOOO=BAAAAAAR"), "Should pass env variables in run configuration  but stdout: ${executionInfo.stdout}")

  }

  @Test
  fun testShouldUseExistingEncodingIfDefinedInMavenOpts() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(params = MavenRunnerParameters(
      true, maven.projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.environmentProperties = mapOf("MAVEN_OPTS" to "-Dfile.encoding=CP866")
                                })
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    assertTrue(executionInfo.stdout.contains("MAVEN_OPTS=-Dfile.encoding=CP866"), "Should pass env variables in run configuration  but stdout: ${executionInfo.stdout}")

  }

  @Test
  @TestFor(issues = ["IDEA-382803"])
  fun testShouldUseExistingEncodingIfDefinedInMavenOptsWithQuotes() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(params = MavenRunnerParameters(
      true, maven.projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.environmentProperties = mapOf("MAVEN_OPTS" to "-Dfile.encoding=\"CP866\"")
                                })
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    assertEquals(Charset.forName("CP866"), executionInfo.charset, "Should take encoding from maven_opts")

  }

  @Test
  fun testShouldUseExistingEncodingIfDefinedInJavaToolsOptions() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(params = MavenRunnerParameters(
      true, maven.projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.environmentProperties = mapOf("JAVA_TOOLS_OPTIONS" to "-Dfile.encoding=CP866")
                                })
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    assertTrue(executionInfo.stdout.contains("JAVA_TOOLS_OPTIONS=-Dfile.encoding=CP866"), "Should pass env variables in run configuration  but stdout: ${executionInfo.stdout}")

  }

  @Test
  fun testShouldExecuteMavenScriptWithPomFile() = runBlocking {

    maven.createModulePom("m1",
                    """
                      <parent>
                          <groupId>test</groupId>
                          <artifactId>project<</artifactId>
                          <version>1</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      """.trimIndent())
    maven.importProjectAsync("""
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
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), "m1", mutableListOf("verify"), emptyList()))
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    assertTrue(executionInfo.system.contains("-f m1"), "Should run build for specified pom but system: ${executionInfo.system}")

  }

  @Test
  fun testShouldExecuteMavenScriptWithVmOptions() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val executionInfo = maven.execute(params = MavenRunnerParameters(
      true, maven.projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                settings = MavenRunnerSettings().also {
                                  it.setVmOptions("-XMyJavaParameter")
                                })
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    shouldContainOption(executionInfo, "-XMyJavaParameter")
  }

  private fun shouldContainOption(executionInfo: ExecutionInfo, option: String) {
    val mavenOptsLineStarts = executionInfo.stdout.indexOf("MAVEN_OPTS=")
    assertTrue(mavenOptsLineStarts != -1, "Should pass env variables in run configuration, but stdout: ${executionInfo.stdout}")
    val mavenOptsLineEnd = executionInfo.stdout.indexOf("\n", mavenOptsLineStarts)
    val mavenOptsLine = executionInfo.stdout.substring(mavenOptsLineStarts, mavenOptsLineEnd)
    assertTrue(mavenOptsLine.contains(option), "MAVEN_OPTS should contain parameters, but was ${mavenOptsLine}")
  }

  @Test
  fun testShouldExecuteMavenScriptWithLocalCache() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    val localCache = if (maven.project.getEelDescriptor().osFamily == EelOsFamily.Windows) {
      "c:\\my\\Path\\To\\Local\\Repository"
    }
    else {
      "/my/Path/To/Local/Repository"
    }
    val executionInfo = maven.execute(params = MavenRunnerParameters(
      true, maven.projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()),
                                generalSettings = maven.mavenGeneralSettings.clone().also {
                                  it.setLocalRepository(localCache)
                                }
    )
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    assertTrue(executionInfo.system.contains(" -Dmaven.repo.local=$localCache "), "Should proper pass local repository: ${executionInfo.system}")

  }

  @Test
  fun testShouldExecuteMavenScriptWithExtension() = runBlocking {
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         """
    )
    createFakeProjectWrapper()
    maven.mavenGeneralSettings.mavenHomeType = MavenWrapper
    addExtensions(MAVEN_EXECUTION_CONFIGURATOR, listOf(
      object : MavenExecutionConfiguratorProvider {
        override fun createConfigurator(environment: ExecutionEnvironment, myConfiguration: MavenRunConfiguration): MavenExecutionConfigurator? {
          return MyTestConfiguratorExtension()
        }

      }
    ), maven.testRootDisposable)
    val executionInfo = maven.execute(params = MavenRunnerParameters(
      true, maven.projectPath.toCanonicalPath(),
      null as String?,
      mutableListOf("verify"), emptyList()))
    assertTrue(executionInfo.stdout.contains(wrapperOutput), "Should run wrapper")
    assertTrue(executionInfo.stdout.contains("MY_ADDED_TEST_ENV_NAME=MY_ADDED_TEST_ENV_VALUE"), "Should execute maven script with env: ${executionInfo.stdout}")
    assertTrue(executionInfo.system.contains(" test-parameter"), "Should execute maven script with parameter: ${executionInfo.stdout}")

  }


  companion object {
    const val wrapperOutput = "WRAPPER REPLACEMENT in Intellij tests"
    const val mavenOutput = "MAVEN REPLACEMENT in Intellij tests"
  }
}

private class MyTestConfiguratorExtension : MavenExecutionConfigurator {
  override fun configureParameters(env: MutableMap<String, String>, parametersList: ParametersList) {
    env["MY_ADDED_TEST_ENV_NAME"] = "MY_ADDED_TEST_ENV_VALUE"
    parametersList.add("test-parameter")
  }
}