// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class MavenExternalParametersVmTest {
  private val maven by mavenFixture()

  @Test
  fun testGetRunVmOptionsSettingsAndJvm() {
    maven.createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    runnerSettings.setVmOptions("-Xmx400m")
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, maven.project, maven.projectPath.toString())
    assertEquals("-Xmx400m", vmOptions)
  }

  @Test
  fun testGetRunVmOptionsSettings() {
    val runnerSettings = MavenRunnerSettings()
    runnerSettings.setVmOptions("-Xmx400m")
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, maven.project, maven.projectPath.toString())
    assertEquals("-Xmx400m", vmOptions)
  }

  @Test
  fun testGetRunVmOptionsJvm() {
    maven.createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, maven.project, maven.projectPath.toString())
    assertEquals("-Xms800m", vmOptions)
  }

  @Test
  fun testGetRunVmOptionsEmpty() {
    val runnerSettings = MavenRunnerSettings()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, maven.project, maven.projectPath.toString())
    assertEmpty(vmOptions)
  }

  @Test
  fun testGetRunVmOptionsSubmoduleConfigParent() {
    maven.createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    val workingDirPath = maven.projectPath.resolve("module").toString()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, maven.project, workingDirPath)
    assertEquals("", vmOptions)
  }

  @Test
  fun testGetRunVmOptionsSubmoduleConfig() {
    maven.createProjectSubFile("module/.mvn/jvm.config", "-Xms800m")
    val runnerSettings = MavenRunnerSettings()
    val workingDirPath = maven.projectPath.resolve("module").toString()
    val vmOptions = MavenExternalParameters.getRunVmOptions(runnerSettings, maven.project, workingDirPath)
    assertEquals("-Xms800m", vmOptions)
  }
}
