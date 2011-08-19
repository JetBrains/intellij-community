package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.Location;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;

public class PythonNoseTestConfigurationProducer extends
                                                 PythonTestConfigurationProducer {
  public PythonNoseTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_NOSETEST_FACTORY);
  }

  protected boolean isAvailable(Location location) {
    PsiElement element = location.getPsiElement();
    return (TestRunnerService.getInstance(element.getProject()).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME));
  }
}