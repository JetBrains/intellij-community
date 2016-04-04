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

/*
 * User: anna
 * Date: 13-May-2010
 */
package com.jetbrains.python.testing.unittest;

import com.intellij.execution.Location;
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

public class PythonUnitTestConfigurationProducer extends PythonTestConfigurationProducer {
  public PythonUnitTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_UNITTEST_FACTORY);
  }

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
                                   @Nullable final AbstractPythonTestRunConfiguration configuration) {
    final boolean isTestFunction = super.isTestFunction(pyFunction, configuration);
    return isTestFunction || (configuration instanceof PythonUnitTestRunConfiguration &&
           !((PythonUnitTestRunConfiguration)configuration).isPureUnittest());
  }

  @Override
  protected boolean isTestClass(@NotNull PyClass pyClass,
                                @Nullable final AbstractPythonTestRunConfiguration configuration,
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