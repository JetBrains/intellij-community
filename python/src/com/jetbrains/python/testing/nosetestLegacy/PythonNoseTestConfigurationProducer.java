// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.nosetestLegacy;

import com.intellij.execution.Location;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.testing.PyTestLegacyInteropKt.isNewTestsModeEnabled;

public final class PythonNoseTestConfigurationProducer extends PythonTestLegacyConfigurationProducer {
  public PythonNoseTestConfigurationProducer() {
    if (isNewTestsModeEnabled()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return PythonTestConfigurationType.getInstance().LEGACY_NOSETEST_FACTORY;
  }

  @Override
  protected boolean isAvailable(@NotNull final Location location) {
    final PsiElement element = location.getPsiElement();
    Module module = location.getModule();
    if (module == null) {
      final Module[] modules = ModuleManager.getInstance(location.getProject()).getModules();
      if (modules.length == 0) return false;
      module = modules[0];
    }
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    return ( PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.NOSE_TEST).equals(TestRunnerService.getInstance(module).getProjectConfiguration()) && sdk != null);
  }

  @Override
  protected boolean isTestFunction(@NotNull final PyFunction pyFunction, @Nullable final AbstractPythonLegacyTestRunConfiguration configuration) {
    return PythonUnitTestUtil.isTestFunction(pyFunction, ThreeState.NO, null);
  }
}