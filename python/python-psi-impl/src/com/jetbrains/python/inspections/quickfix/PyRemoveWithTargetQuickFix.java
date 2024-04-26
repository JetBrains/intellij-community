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

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithItem;
import org.jetbrains.annotations.NotNull;

public class PyRemoveWithTargetQuickFix extends PsiUpdateModCommandQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.with.target");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyWithItem withItem = PsiTreeUtil.getParentOfType(element, PyWithItem.class);
    if (withItem == null) return;
    final PyExpression withTarget = withItem.getTarget();
    if (withTarget != element) return;
    final PyExpression withExpr = withItem.getExpression();
    withItem.deleteChildRange(withExpr.getNextSibling(), withItem.getLastChild());
  }
}
