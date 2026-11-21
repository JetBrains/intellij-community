package com.jetbrains.python.testing

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.idea.TestFor
import com.intellij.psi.impl.source.PsiFileImpl
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.run.PythonRunConfiguration
import junit.framework.TestCase

@Subsystems.TestRunner
@Components.Pytest
@Layers.Functional
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

  @TestFor(issues = ["PY-94294"])
  fun testNonTestModuleIsRejectedWithoutLoadingItsAst() {
    val file = myFixture.addFileToProject("app/models.py", """
      class Base:
          pass


      class Model(Base):
          def describe(self):
              '''Describes the model.'''
              return 'model'
      """.trimIndent())
    val context = ConfigurationContext(file)
    val producer = RunConfigurationProducer.EP_NAME.extensionList
      .filterIsInstance<AbstractPythonTestConfigurationProducer<*>>()
      .single { it.configurationClass == PyAbstractTestConfiguration::class.java }

    assertFalse((file as PsiFileImpl).isContentsLoaded)

    assertNull(producer.createConfigurationFromContext(context))
    assertFalse("the file check must reject the file before the test and doctest checks read it",
                file.isContentsLoaded)
  }
}
