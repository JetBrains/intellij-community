/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to replace statement that has no effect with function call
 */
public class StatementEffectFunctionCallQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.statement.effect");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression != null && expression.isWritable() && expression instanceof PyReferenceExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      if ("print".equals(expression.getText()))
        replacePrint(expression, elementGenerator);
      else
        expression.replace(elementGenerator.createCallExpression(LanguageLevel.forElement(expression), expression.getText()));
    }
  }

  private static void replacePrint(PsiElement expression, PyElementGenerator elementGenerator) {
    StringBuilder stringBuilder = new StringBuilder("print (");

    final PsiElement whiteSpace = expression.getContainingFile().findElementAt(expression.getTextOffset() + expression.getTextLength());
    PsiElement next = null;
    if (whiteSpace instanceof PsiWhiteSpace) {
      final String whiteSpaceText = whiteSpace.getText();
      if (!whiteSpaceText.contains("\n")) {
        next = whiteSpace.getNextSibling();
        while (next instanceof PsiWhiteSpace && whiteSpaceText.contains("\\")) {
          next = next.getNextSibling();
        }
      }
    }
    else
      next = whiteSpace;

    RemoveUnnecessaryBackslashQuickFix.removeBackSlash(next);
    if (whiteSpace != null) whiteSpace.delete();
    if (next != null) {
      final String text = next.getText();
      stringBuilder.append(text);
      if (text.endsWith(","))
        stringBuilder.append(" end=' '");
      next.delete();
    }
    stringBuilder.append(")");
    expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpression.class,
                                                       stringBuilder.toString()));
  }
}
