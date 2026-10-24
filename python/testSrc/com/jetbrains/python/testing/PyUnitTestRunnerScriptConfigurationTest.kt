// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndGet
import com.jetbrains.python.PyBundle
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdom.Element
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.pathString

@Subsystems.TestRunner
@Layers.Functional
@TestFor(issues = ["PY-26068"], classes = [PyUnitTestConfiguration::class])
class PyUnitTestRunnerScriptConfigurationTest : PyCodeInsightTestCase() {
  @Test
  fun `runner script is saved and loaded`() {
    val element = Element("configuration")
    createConfiguration().apply { runnerScript = "runtests.py" }.writeExternal(element)

    val loaded = createConfiguration().apply { readExternal(element) }

    assertThat(loaded.runnerScript).isEqualTo("runtests.py")
  }

  @Test
  fun `relative runner script starts at the working directory`(@TempDir workingDirectory: Path) {
    val configuration = createConfiguration().apply {
      this.workingDirectory = workingDirectory.pathString
      runnerScript = "runtests.py"
    }

    assertThat(configuration.getRunnerScriptPath()).isEqualTo(workingDirectory.resolve("runtests.py"))
  }

  @Test
  fun `absolute runner script is used as is`(@TempDir workingDirectory: Path) {
    val script = workingDirectory.resolve("other").resolve("runtests.py")
    val configuration = createConfiguration().apply {
      this.workingDirectory = workingDirectory.pathString
      runnerScript = script.pathString
    }

    assertThat(configuration.getRunnerScriptPath()).isEqualTo(script)
  }

  @Test
  fun `blank runner script runs unittest`() {
    val configuration = createConfiguration().apply { runnerScript = "  " }

    assertThat(configuration.getRunnerScriptPath()).isNull()
  }

  @Test
  fun `pattern is not given to the runner script`(@TempDir workingDirectory: Path) {
    val testsDirectory = workingDirectory.resolve("tests").createDirectory()
    val configuration = createConfiguration().apply {
      this.workingDirectory = workingDirectory.pathString
      target.target = testsDirectory.pathString
      target.targetType = PyRunTargetVariant.PATH
      pattern = "test_*.py"
    }
    assertThat(configuration.getTestSpec()).containsExactly("--path", testsDirectory.pathString, "--", "-p", "test_*.py")

    configuration.runnerScript = "runtests.py"

    assertThat(configuration.getTestSpec()).containsExactly("--path", testsDirectory.pathString)
  }

  @Test
  fun `invalid runner script is a configuration error`() {
    val configuration = createConfiguration().apply {
      target.targetType = PyRunTargetVariant.CUSTOM
      runnerScript = "run\u0000tests.py"
    }

    assertThatThrownBy { configuration.checkConfiguration() }
      .isInstanceOf(RuntimeConfigurationError::class.java)
      .hasMessage(PyBundle.message("python.testing.runner.script.invalid", configuration.runnerScript))
  }

  @Test
  fun `configuration from context runs in the runner script directory`(@TestDisposable disposable: Disposable) {
    val factory = PythonTestConfigurationType.getInstance().unitTestFactory
    val testRunnerService = TestRunnerService.getInstance(myFixture.module)
    val originalFactory = testRunnerService.selectedFactory
    val template = RunManager.getInstance(myFixture.project).getConfigurationTemplate(factory).configuration as PyUnitTestConfiguration
    testRunnerService.selectedFactory = factory
    Disposer.register(disposable) {
      template.runnerScript = null
      testRunnerService.selectedFactory = originalFactory
    }
    myFixture.configureByText("test_sample.py", """
      import unittest

      class SampleTest(unittest.TestCase):
          def test_<caret>pass(self):
              pass
    """.trimIndent())
    val scriptDirectory = myFixture.file.virtualFile.toNioPath().parent
    template.runnerScript = scriptDirectory.resolve("runtests.py").pathString

    val configuration = runInEdtAndGet {
      val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
      ConfigurationContext(element).configurationsFromContext.orEmpty()
        .map { it.configuration }
        .filterIsInstance<PyUnitTestConfiguration>()
        .single()
    }

    assertThat(configuration.runnerScript).isEqualTo(template.runnerScript)
    assertThat(configuration.workingDirectory).isEqualTo(scriptDirectory.pathString)
  }

  private fun createConfiguration(): PyUnitTestConfiguration =
    PythonTestConfigurationType.getInstance().unitTestFactory.createTemplateConfiguration(myFixture.project)
}
