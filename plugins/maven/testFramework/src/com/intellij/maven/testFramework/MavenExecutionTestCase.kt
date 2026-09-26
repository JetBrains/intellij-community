// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

@TestApplication
abstract class MavenExecutionTestCase(mavenVersion: String, modelVersion: String) {
  protected val maven: MavenImportingTestFixture by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
  )

  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUpMavenExecutionJdk() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, JDK_NAME)
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.setUp() })
    }
  }

  @AfterEach
  fun tearDownMavenExecutionJdk() {
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.tearDown() })
    }
  }

  companion object {
    private const val JDK_NAME = "MavenExecutionTestJDK"
  }
}
