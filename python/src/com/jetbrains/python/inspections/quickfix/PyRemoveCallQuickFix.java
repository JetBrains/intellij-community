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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import org.jetbrains.annotations.NotNull;

public class PyRemoveCallQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.NAME.remove.call");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement call = descriptor.getPsiElement();
    assert call instanceof PyCallExpression;
    if (call instanceof PyDecorator) {
      call.delete();
    }
    else {
      final PyArgumentList argumentList = ((PyCallExpression)call).getArgumentList();
      assert argumentList != null;
      argumentList.delete();
    }
  }
}
