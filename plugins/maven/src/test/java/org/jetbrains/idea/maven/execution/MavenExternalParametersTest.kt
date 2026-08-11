// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenExternalParametersTest(mavenVersion: String, modelVersion: String) {

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
  }

  @AfterEach
  fun tearDownJdk() {
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.tearDown() })
    })
  }

  @Test
  fun testBundledMavenHome() {
    val runnerParameters = MavenRunnerParameters(maven.projectPath.toCanonicalPath(), null, false, mutableListOf<String?>(), mutableMapOf<String?, Boolean?>())
    val generalSettings = MavenProjectsManager.getInstance(maven.project).getGeneralSettings()
    val parameters = MavenExternalParameters.createJavaParameters(maven.project, runnerParameters, generalSettings, null, null)
    assertTrue(parameters.vmParametersList.hasProperty("maven.home"))
  }

  @Test
  fun testWrappedMavenWithoutWrapperProperties() {
    val runnerParameters = MavenRunnerParameters(maven.projectPath.toCanonicalPath(), null, false, mutableListOf<String?>(), mutableMapOf<String?, Boolean?>())
    val generalSettings = MavenProjectsManager.getInstance(maven.project).getGeneralSettings()
    generalSettings.mavenHomeType = MavenWrapper
    val parameters = MavenExternalParameters.createJavaParameters(maven.project, runnerParameters, generalSettings, null, null)
    assertTrue(parameters.vmParametersList.hasProperty("maven.home"))
  }
}