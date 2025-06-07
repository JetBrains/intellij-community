// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ReplaceListComprehensionWithForIntention extends PsiUpdateModCommandAction<PsiElement> {
  ReplaceListComprehensionWithForIntention() {
    super(PsiElement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.replace.list.comprehensions.with.for");
  }


  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    PyListCompExpression expression =
      PsiTreeUtil.getTopmostParentOfType(element, PyListCompExpression.class);
    if (expression == null) {
      return null;
    }
    if (expression.getComponents().isEmpty()) return null;
    PsiElement parent = expression.getParent();
    if (parent instanceof PyAssignmentStatement || parent instanceof PyPrintStatement) {
      return super.getPresentation(context, element);
    }
    return null;
  }


  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyListCompExpression expression = PsiTreeUtil.getTopmostParentOfType(element, PyListCompExpression.class);
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());

    if (parent instanceof PyAssignmentStatement) {
      final PsiElement leftExpr = ((PyAssignmentStatement)parent).getLeftHandSideExpression();
      if (leftExpr == null) return;
      PyAssignmentStatement initAssignment = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyAssignmentStatement.class,
                                                                             leftExpr.getText() + " = []");
      PyForStatement forStatement = createForLoop(expression, elementGenerator,
                                                  leftExpr.getText() + ".append("+ expression.getResultExpression().getText() +")");

      parent.getParent().addBefore(initAssignment, parent);
      parent.replace(forStatement);

    }
    else if (parent instanceof PyPrintStatement) {
      PyForStatement forStatement = createForLoop(expression, elementGenerator, "print " + "(" + expression.getResultExpression().getText() +")");
      parent.replace(forStatement);
    }
  }

  private static PyForStatement createForLoop(final PyListCompExpression expression, final PyElementGenerator elementGenerator,
                                              final String result) {
    final List<PyComprehensionComponent> components = expression.getComponents();
    final StringBuilder stringBuilder = new StringBuilder();
    int slashNum = 1;
    for (PyComprehensionComponent component : components) {
      if (component instanceof PyComprehensionForComponent) {
        stringBuilder.append("for ");
        stringBuilder.append(((PyComprehensionForComponent)component).getIteratorVariable().getText());
        stringBuilder.append(" in ");
        stringBuilder.append(((PyComprehensionForComponent)component).getIteratedList().getText());
        stringBuilder.append(":\n");
      }
      if (component instanceof PyComprehensionIfComponent) {
        final PyExpression test = ((PyComprehensionIfComponent)component).getTest();
        if (test != null) {
          stringBuilder.append("if ");
          stringBuilder.append(test.getText());
          stringBuilder.append(":\n");
        }
      }
      stringBuilder.append("\t".repeat(slashNum));
      ++slashNum;
    }
    stringBuilder.append(result);
    return elementGenerator.createFromText(LanguageLevel.forElement(expression), PyForStatement.class,
                             stringBuilder.toString());
  }
}
