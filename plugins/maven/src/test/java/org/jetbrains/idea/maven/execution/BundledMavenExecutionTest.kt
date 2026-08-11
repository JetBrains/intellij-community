// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.checkUpdatingExcludedFoldersAfterExecution
import org.jetbrains.idea.maven.fixtures.toggleScriptsRegistryKey
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class BundledMavenExecutionTest(mavenVersion: String, modelVersion: String) {

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
    maven.toggleScriptsRegistryKey(false)
  }

  @AfterEach
  fun tearDown() {
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.tearDown() })
    })
  }

  @Test
  fun testUpdatingExcludedFoldersAfterExecution() = runBlocking {
    maven.checkUpdatingExcludedFoldersAfterExecution()
  }
}