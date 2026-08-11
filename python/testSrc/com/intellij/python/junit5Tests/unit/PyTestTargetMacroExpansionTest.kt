// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.testing.PyTestConfiguration
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The path target of a test run configuration may contain path macros such as `$ProjectFileDir$`.
 * They must be expanded before the path is passed to the test runner (PY-90680).
 */
@TestApplication
class PyTestTargetMacroExpansionTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project get() = projectFixture.get()

  @Test
  fun `path target macros are expanded`() {
    val configuration = PyTestConfiguration(project, PyTestFactory(PythonTestConfigurationType.getInstance()))
    configuration.target.targetType = PyRunTargetVariant.PATH
    configuration.target.target = "\$ProjectFileDir\$/tests"

    val arguments = configuration.target.generateArgumentsLine(configuration)

    assertEquals(2, arguments.size, "Expected '--path <dir>', got: $arguments")
    assertEquals("--path", arguments[0])
    assertFalse(arguments[1].contains("\$ProjectFileDir\$"), "Macro was not expanded: ${arguments[1]}")
    assertEquals(Path.of(project.basePath!!, "tests"), Path.of(arguments[1]))
  }
}
