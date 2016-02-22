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
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.run.RunnableScriptFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PythonUnitTestRunnableScriptFilter implements RunnableScriptFilter {
  public boolean isRunnableScript(PsiFile script, @NotNull Module module, Location location, @Nullable final TypeEvalContext context) {
    return script instanceof PyFile && PythonUnitTestUtil.getTestCaseClassesFromFile(script, context).size() > 0
           && !isIfNameMain(location) && TestRunnerService.getInstance(module).getProjectConfiguration().
      equals(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);
  }

  public static boolean isIfNameMain(Location location) {
    PsiElement element = location.getPsiElement();
    while (true) {
      final PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
      if (ifStatement == null) {
        break;
      }
      element = ifStatement;
    }
    if (element instanceof PyIfStatement) {
      PyIfStatement ifStatement = (PyIfStatement)element;
      return PyUtil.isIfNameEqualsMain(ifStatement);
    }
    return false;
  }
}
