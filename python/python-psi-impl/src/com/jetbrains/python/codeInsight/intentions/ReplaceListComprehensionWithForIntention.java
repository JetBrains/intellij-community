// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStatementListImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ReplaceListComprehensionWithForIntention extends PyBaseIntentionAction {
  @Override
  @NotNull
  public String getText() {
    return PyPsiBundle.message("INTN.replace.list.comprehensions.with.for");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.replace.list.comprehensions.with.for");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyListCompExpression expression =
      PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyListCompExpression.class);
    if (expression == null) {
      return false;
    }
    if (expression.getComponents().isEmpty()) return false;
    PsiElement parent = expression.getParent();
    if (parent instanceof PyAssignmentStatement || parent instanceof PyPrintStatement) {
      return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyListCompExpression expression = PsiTreeUtil.getTopmostParentOfType(
        file.findElementAt(editor.getCaretModel().getOffset()), PyListCompExpression.class);
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    if (parent instanceof PyAssignmentStatement) {
      final PsiElement leftExpr = ((PyAssignmentStatement)parent).getLeftHandSideExpression();
      if (leftExpr == null) return;
      PyAssignmentStatement initAssignment = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyAssignmentStatement.class,
                                                                         leftExpr.getText() + " = []");
      PyForStatement forStatement = createForLoop(expression, elementGenerator,
                                                  leftExpr.getText() + ".append("+ expression.getResultExpression().getText() +")");

      PyStatementList stList = new PyStatementListImpl(initAssignment.getNode());
      stList.add(initAssignment);
      stList.add(forStatement);
      stList.getStatements()[0].delete();
      parent.replace(stList);

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
