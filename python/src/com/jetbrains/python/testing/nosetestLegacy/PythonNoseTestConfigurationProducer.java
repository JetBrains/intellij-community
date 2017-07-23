/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.nosetestLegacy;

import com.intellij.execution.Location;
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

public class PythonNoseTestConfigurationProducer extends
                                                 PythonTestLegacyConfigurationProducer {
  public PythonNoseTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().LEGACY_NOSETEST_FACTORY);
  }

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