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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyYieldFromIntention extends PyBaseIntentionAction {
  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("INTN.yield.from");
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("INTN.yield.from");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!LanguageLevel.forElement(file).isPython2()) {
      final PyForStatement forLoop = findForStatementAtCaret(editor, file);
      if (forLoop != null) {
        final PyTargetExpression forTarget = findSingleForLoopTarget(forLoop);
        final PyReferenceExpression yieldValue = findSingleYieldValue(forLoop);
        if (forTarget != null && yieldValue != null) {
          final String targetName = forTarget.getName();
          if (targetName != null && targetName.equals(yieldValue.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyForStatement forLoop = findForStatementAtCaret(editor, file);
    if (forLoop != null) {
      final PyExpression source = forLoop.getForPart().getSource();
      if (source != null) {
        final PyElementGenerator generator = PyElementGenerator.getInstance(project);
        final String text = "yield from foo";
        final PyExpressionStatement exprStmt = generator.createFromText(LanguageLevel.forElement(file), PyExpressionStatement.class, text);
        final PyExpression expr = exprStmt.getExpression();
        if (expr instanceof PyYieldExpression) {
          final PyExpression yieldValue = ((PyYieldExpression)expr).getExpression();
          if (yieldValue != null) {
            yieldValue.replace(source);
            forLoop.replace(exprStmt);
          }
        }
      }
    }
  }

  @Nullable
  private static PyForStatement findForStatementAtCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement elementAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
    return PsiTreeUtil.getParentOfType(elementAtCaret, PyForStatement.class);
  }

  @Nullable
  private static PyTargetExpression findSingleForLoopTarget(@NotNull PyForStatement forLoop) {
    final PyForPart forPart = forLoop.getForPart();
    final PyExpression forTarget = forPart.getTarget();
    if (forTarget instanceof PyTargetExpression) {
      return (PyTargetExpression)forTarget;
    }
    return null;
  }

  @Nullable
  private static PyReferenceExpression findSingleYieldValue(@NotNull PyForStatement forLoop) {
    final PyForPart forPart = forLoop.getForPart();
    if (forLoop.getElsePart() == null) {
      final PyStatement[] statements = forPart.getStatementList().getStatements();
      if (statements.length == 1) {
        final PyStatement firstStmt = statements[0];
        if (firstStmt instanceof PyExpressionStatement) {
          final PyExpression firstExpr = ((PyExpressionStatement)firstStmt).getExpression();
          if (firstExpr instanceof PyYieldExpression) {
            final PyYieldExpression yieldExpr = (PyYieldExpression)firstExpr;
            final PyExpression yieldValue = yieldExpr.getExpression();
            if (yieldValue instanceof PyReferenceExpression) {
              return (PyReferenceExpression)yieldValue;
            }
          }
        }
      }
    }
    return null;
  }
}
