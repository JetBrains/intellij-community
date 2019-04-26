// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.unittestLegacy;

import com.intellij.execution.Location;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.testing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.testing.PyTestLegacyInteropKt.isNewTestsModeEnabled;

public final class PythonUnitTestConfigurationProducer extends PythonTestLegacyConfigurationProducer {
  public PythonUnitTestConfigurationProducer() {
    if (isNewTestsModeEnabled()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return PythonTestConfigurationType.getInstance().LEGACY_UNITTEST_FACTORY;
  }

  @Override
  protected boolean isAvailable(@NotNull final Location location) {
    PsiElement element = location.getPsiElement();
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) return false;
    if ((TestRunnerService.getInstance(module).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME))) {
      return true;
    }
    return false;
  }

  @Override
  protected boolean isTestFunction(@NotNull final PyFunction pyFunction,
                                   @Nullable final AbstractPythonLegacyTestRunConfiguration configuration) {
    final boolean isTestFunction = super.isTestFunction(pyFunction, configuration);
    return isTestFunction || (configuration instanceof PythonUnitTestRunConfiguration &&
           !((PythonUnitTestRunConfiguration)configuration).isPureUnittest());
  }

  @Override
  protected boolean isTestClass(@NotNull PyClass pyClass,
                                @Nullable final AbstractPythonLegacyTestRunConfiguration configuration,
                                TypeEvalContext context) {
    final boolean isTestClass = super.isTestClass(pyClass, configuration, context);
    return isTestClass || (configuration instanceof PythonUnitTestRunConfiguration &&
                           !((PythonUnitTestRunConfiguration)configuration).isPureUnittest());
  }

  @Override
  protected boolean isTestFile(@NotNull final PyFile file) {
    if (PyNames.SETUP_DOT_PY.equals(file.getName())) return true;
    final List<PyStatement> testCases = getTestCaseClassesFromFile(file);
    if (testCases.isEmpty()) return false;
    return true;
  }
}