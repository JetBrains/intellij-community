package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.Location;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.*;

public class PythonNoseTestConfigurationProducer extends
                                                 PythonTestConfigurationProducer {
  public PythonNoseTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_NOSETEST_FACTORY);
  }

  protected boolean isAvailable(Location location) {
    PsiElement element = location.getPsiElement();
    final Sdk sdk = PythonSdkType.findPythonSdk(location.getModule());
    return (TestRunnerService.getInstance(element.getProject()).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME) && sdk != null &&
            PyTestFrameworksUtil.isNoseTestInstalled(sdk.getHomePath()));
  }

}