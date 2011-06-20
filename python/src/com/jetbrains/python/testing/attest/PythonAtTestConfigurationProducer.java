package com.jetbrains.python.testing.attest;

import com.google.common.collect.Lists;
import com.intellij.execution.Location;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;

import java.util.List;
/**
 * User: catherine
 */
public class PythonAtTestConfigurationProducer extends
                                                 PythonTestConfigurationProducer {
  public PythonAtTestConfigurationProducer() {
    super(PythonAtTestRunConfigurationType.class);
  }

  protected boolean isAvailable(Location location) {
    PsiElement element = location.getPsiElement();
    return (TestRunnerService.getInstance(element.getProject()).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME));
  }

  protected boolean isTestClass(PyClass pyClass) {
    if (pyClass == null) return false;
    for (PyClassRef an : pyClass.iterateAncestors()) {
      if ("TestBase".equals(an.getClassName()) && hasTestFunction(pyClass))
        return true;
    }
    return false;
  }

  private boolean hasTestFunction(PyClass pyClass) {
    PyFunction[] methods = pyClass.getMethods();
    for (PyFunction function : methods) {
      PyDecoratorList decorators = function.getDecoratorList();
      if (decorators == null) continue;
      for (PyDecorator decorator : decorators.getDecorators()) {
        if ("test".equals(decorator.getName()) || "test_if".equals(decorator.getName()))
          return true;
      }
    }
    return false;
  }

  protected boolean isTestFunction(PyFunction pyFunction) {
    if (pyFunction == null)return false;
    PyDecoratorList decorators = pyFunction.getDecoratorList();
    if (decorators == null) return false;
    for (PyDecorator decorator : decorators.getDecorators()) {
      if ("test".equals(decorator.getName()) || "test_if".equals(decorator.getName()))
        return true;
    }
    return false;
  }

  protected List<PyStatement> getTestCaseClassesFromFile(PyFile file) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isTestClass(cls)) {
        result.add(cls);
      }
    }

    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isTestFunction(cls)) {
        result.add(cls);
      }
    }
    return result;
  }
}