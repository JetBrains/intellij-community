// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.MavenExecutionTestCaseLegacy
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWrapper
import org.junit.Test

class MavenExternalParametersTest : MavenExecutionTestCaseLegacy() {
  @Test
  fun testBundledMavenHome() {
    val runnerParameters = MavenRunnerParameters(projectPath, null, false, mutableListOf<String?>(), mutableMapOf<String?, Boolean?>())
    val generalSettings = MavenProjectsManager.getInstance(project).getGeneralSettings()
    val parameters = MavenExternalParameters.createJavaParameters(project, runnerParameters, generalSettings, null, null)
    assertTrue(parameters.getVMParametersList().hasProperty("maven.home"))
  }

  @Test
  fun testWrappedMavenWithoutWrapperProperties() {
    val runnerParameters = MavenRunnerParameters(projectPath, null, false, mutableListOf<String?>(), mutableMapOf<String?, Boolean?>())
    val generalSettings = MavenProjectsManager.getInstance(project).getGeneralSettings()
    generalSettings.mavenHomeType = MavenWrapper
    val parameters = MavenExternalParameters.createJavaParameters(project, runnerParameters, generalSettings, null, null)
    assertTrue(parameters.getVMParametersList().hasProperty("maven.home"))
  }
}