package com.jetbrains.python.testing

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.run.PythonRunConfiguration
import junit.framework.TestCase

class PyTestConfigurationProducerRespectFilePatternTest : PyTestCase() {
  companion object {
    private const val TEST_DIR = "/pyTestConfigurationProducer/"
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + TEST_DIR

  override fun setUp() {
    super.setUp()
    // Ensure pytest is the selected factory so PyTestsConfigurationProducer is active
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
  }

  fun testDoNotProducePyTestConfigForNonTestModule() {
    val psiFile = myFixture.configureByFile("module.py")
    val element = psiFile.findElementAt(myFixture.caretOffset)
    assertNotNull("Caret element not found", element)

    val configurations = ConfigurationContext(element!!).configurationsFromContext
    assertNotNull("No configurations produced from context", configurations)
    // Exactly one configuration should be produced
    TestCase.assertEquals("Expected exactly one configuration", 1, configurations!!.size)

    val config: RunConfiguration = configurations[0].configurationSettings.configuration
    // In a non-test module, pytest configuration must NOT be produced; instead, a Python script run configuration is expected
    assertTrue(
      "Expected PythonRunConfiguration for non-test module, but got ${config::class.java}",
      config is PythonRunConfiguration
    )
  }
}