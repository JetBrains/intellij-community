/*
 * User: anna
 * Date: 13-May-2010
 */
package com.jetbrains.python.testing.unittest;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import com.jetbrains.python.testing.TestRunnerService;
import org.jetbrains.annotations.Nullable;

public class PythonUnitTestConfigurationProducer extends PythonTestConfigurationProducer {
  public PythonUnitTestConfigurationProducer() {
    super(PythonUnitTestConfigurationType.class);
  }

  protected boolean isAvailable(Location location) {
    PsiElement element = location.getPsiElement();
    return (TestRunnerService.getInstance(element.getProject()).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME));
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationFromFunction(Location location, PyElement element) {
    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from function");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    if (configuration.isPureUnittest() && !isUnitTestFunction(pyFunction))
      return null;

    return super.createConfigurationFromFunction(location, element);
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationFromClass(Location location, PyElement element) {
    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from class");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (configuration.isPureUnittest() && !isUnitTestCaseClass(pyClass)) return null;
    return super.createConfigurationFromClass(location, element);
  }

  private static boolean isUnitTestFunction(PyFunction pyFunction) {
    if (pyFunction == null || !PythonUnitTestUtil.isUnitTestCaseFunction(pyFunction)) return false;
    return true;
  }

  private static boolean isUnitTestCaseClass(PyClass pyClass) {
    if (pyClass == null || !PythonUnitTestUtil.isUnitTestCaseClass(pyClass)) return false;
    return true;
  }

  protected boolean isTestFile(PsiElement file) {
    if (file == null || !(file instanceof PyFile) || !PythonUnitTestUtil.isUnitTestFile((PyFile)file)) return false;
    return true;
  }

}