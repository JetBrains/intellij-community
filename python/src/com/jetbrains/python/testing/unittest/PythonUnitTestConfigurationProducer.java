/*
 * User: anna
 * Date: 13-May-2010
 */
package com.jetbrains.python.testing.unittest;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.testing.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PythonUnitTestConfigurationProducer extends PythonTestConfigurationProducer {
  public PythonUnitTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_UNITTEST_FACTORY);
  }

  protected boolean isAvailable(Location location) {
    PsiElement element = location.getPsiElement();
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) return false;
    if ((TestRunnerService.getInstance(module).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME))) {
      if (element instanceof PsiDirectory) {
        final PyTestVisitor visitor = new PyTestVisitor();
        element.accept(visitor);
        return visitor.hasTests;
      }
      else return true;
    }
    return false;
  }

  private static class PyTestVisitor extends PyRecursiveElementVisitor {
    boolean hasTests = false;

    @Override
    public void visitPyFile(PyFile node) {
      List<PyStatement> testClasses = PythonUnitTestUtil.getTestCaseClassesFromFile(node);
      if (!testClasses.isEmpty()) hasTests = true;
    }
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
}