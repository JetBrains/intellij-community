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

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PyRemoveAssignmentStatementTargetQuickFix extends PsiUpdateModCommandAction<PsiElement> {

  public PyRemoveAssignmentStatementTargetQuickFix(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return Objects.requireNonNull(super.getPresentation(context, element)).withPriority(PriorityAction.Priority.HIGH);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.assignment.target");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (assignmentStatement == null) return;
    if (assignmentStatement.getRawTargets().length == 1) {
      final PyExpression expression = assignmentStatement.getAssignedValue();
      if (expression == null) return;
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
      PyExpressionStatement statement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class,
                                                                        expression.getText());
      assignmentStatement.replace(statement);
    }
    else {
      PsiElement possibleNextEq = PsiTreeUtil.nextVisibleLeaf(element);
      if (possibleNextEq == null) return;
      assert possibleNextEq.getNode().getElementType() == PyTokenTypes.EQ;
      element.getParent().deleteChildRange(element, possibleNextEq);
    }
  }
}
