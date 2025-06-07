// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.showCase

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.python.junit5Tests.framework.mockPathAndAdd
import com.intellij.python.junit5Tests.framework.unMockPath
import com.intellij.util.EnvironmentUtil
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * This is how you use [mockPathAndAdd]:
 * Extend a case with [SystemStubsExtension], add [EnvironmentVariables], mark it with [SystemStub], call [mockPathAndAdd], [unMockPath]
 */
@ExtendWith(SystemStubsExtension::class)
class EnvironmentVariablesPathMockTest {

  @SystemStub
  private val environment = EnvironmentVariables()

  @TempDir
  private lateinit var fakeDir: Path


  @BeforeEach
  fun setUp() {
    PathEnvironmentVariableUtil.findInPath("someData") // Let this class cache something to see how we mock it
    environment.mockPathAndAdd(fakeDir)
  }

  @AfterEach
  fun tearDown() {
    environment.unMockPath()
  }

  @Test
  fun ensurePathFixedTest() {
    Assertions.assertTrue(System.getenv().keys.count() > 2, "Env is too small, seems to be broken")
    val pathKey = System.getenv().keys.find { it.lowercase() == "path" }
    Assertions.assertNotNull(pathKey, "Env doesn't have path")
    MatcherAssert.assertThat("System.env hasn't been changed",
                             System.getenv(pathKey!!),
                             CoreMatchers.containsString(fakeDir.pathString))
    MatcherAssert.assertThat("PathEnvironmentVariableUtil hasn't been changed",
                             PathEnvironmentVariableUtil.getPathVariableValue(),
                             CoreMatchers.containsString(fakeDir.pathString))
    for (envVarKey in System.getenv().keys) {
      // Make sure we didn't break other vars for EnvironmentUtil
      Assertions.assertNotNull(EnvironmentUtil.getValue(envVarKey), "env $envVarKey is broken in EnvironmentUtil")
    }
  }
}