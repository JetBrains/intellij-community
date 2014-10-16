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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStatementListImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: catherine
 */
public class ReplaceListComprehensionWithForIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.replace.list.comprehensions.with.for");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.replace.list.comprehensions.with.for");
  }

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

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
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
    final List<ComprehensionComponent> components = expression.getComponents();
    final StringBuilder stringBuilder = new StringBuilder();
    int slashNum = 1;
    for (ComprehensionComponent component : components) {
      if (component instanceof ComprhForComponent) {
        stringBuilder.append("for ");
        stringBuilder.append(((ComprhForComponent)component).getIteratorVariable().getText());
        stringBuilder.append(" in ");
        stringBuilder.append(((ComprhForComponent)component).getIteratedList().getText());
        stringBuilder.append(":\n");
      }
      if (component instanceof ComprhIfComponent) {
        final PyExpression test = ((ComprhIfComponent)component).getTest();
        if (test != null) {
          stringBuilder.append("if ");
          stringBuilder.append(test.getText());
          stringBuilder.append(":\n");
        }
      }
      for (int i = 0; i != slashNum; ++i)
        stringBuilder.append("\t");
      ++slashNum;
    }
    stringBuilder.append(result);
    return elementGenerator.createFromText(LanguageLevel.forElement(expression), PyForStatement.class,
                             stringBuilder.toString());
  }

 public boolean startInWriteAction() {
    return true;
  }
}