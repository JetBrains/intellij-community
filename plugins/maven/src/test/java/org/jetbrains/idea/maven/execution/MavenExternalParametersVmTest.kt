// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.MavenTestCaseLegacy
import junit.framework.TestCase
import java.io.IOException
import java.nio.file.Path

class MavenExternalParametersVmTest : MavenTestCaseLegacy() {
  @Throws(IOException::class)
  fun testGetRunVmOptionsSettingsAndJvm() {
    createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    runnerSettings.setVmOptions("-Xmx400m")
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, project, projectPath)
    TestCase.assertEquals("-Xmx400m", vmOptions)
  }

  fun testGetRunVmOptionsSettings() {
    val runnerSettings = MavenRunnerSettings()
    runnerSettings.setVmOptions("-Xmx400m")
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, project, projectPath)
    TestCase.assertEquals("-Xmx400m", vmOptions)
  }

  @Throws(IOException::class)
  fun testGetRunVmOptionsJvm() {
    createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, project, projectPath)
    TestCase.assertEquals("-Xms800m", vmOptions)
  }

  @Throws(IOException::class)
  fun testGetRunVmOptionsEmpty() {
    val runnerSettings = MavenRunnerSettings()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, project, projectPath)
    assertEmpty(vmOptions)
  }

  @Throws(IOException::class)
  fun testGetRunVmOptionsSubmoduleConfigParent() {
    createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    val workingDirPath = Path.of(projectPath).resolve("module").toString()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, project, workingDirPath)
    TestCase.assertEquals("", vmOptions)
  }

  @Throws(IOException::class)
  fun testGetRunVmOptionsSubmoduleConfig() {
    createProjectSubFile("/module/.mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    val workingDirPath = Path.of(projectPath).resolve("module").toString()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, project, workingDirPath)
    TestCase.assertEquals("-Xms800m", vmOptions)
  }
}