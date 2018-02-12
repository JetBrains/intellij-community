/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyKeyValueExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PyRemoveDictKeyQuickFix implements LocalQuickFix {

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.NAME.remove.dict.key");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyKeyValueExpression expression = PsiTreeUtil.getParentOfType(element, PyKeyValueExpression.class);
    if (expression == null) return;
    final PsiElement nextSibling = PsiTreeUtil.skipWhitespacesForward(expression);
    final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(expression);
    expression.delete();
    if (nextSibling != null && nextSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
      nextSibling.delete();
      return;
    }
    if (prevSibling != null && prevSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
      prevSibling.delete();
    }
  }
}
