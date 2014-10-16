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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.ConvertDocstringQuickFix;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * Inspection to detect docstrings not using triple double-quoted string
 */
public class PySingleQuotedDocstringInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.single.quoted.docstring");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyStringLiteralExpression(final PyStringLiteralExpression string) {
      String stringText = string.getText();
      int length = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      stringText = stringText.substring(length);
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string)  {
          if (!stringText.startsWith("\"\"\"") && !stringText.endsWith("\"\"\"")) {
            ProblemsHolder holder = getHolder();
            if (holder != null) {
              int quoteCount = 1;
              if (stringText.startsWith("'''") && stringText.endsWith("'''")) {
                quoteCount = 3;
              }
              TextRange trStart = new TextRange(length, length+quoteCount);
              TextRange trEnd = new TextRange(stringText.length()+length-quoteCount,
                                              stringText.length()+length);
              if (string.getStringValue().isEmpty())
                holder.registerProblem(string, PyBundle.message("INSP.message.single.quoted.docstring"),
                                       new ConvertDocstringQuickFix());
              else {
                holder.registerProblem(string, trStart,
                                       PyBundle.message("INSP.message.single.quoted.docstring"), new ConvertDocstringQuickFix());
                holder.registerProblem(string, trEnd,
                                       PyBundle.message("INSP.message.single.quoted.docstring"), new ConvertDocstringQuickFix());
              }
            }
          }
        }
      }
    }
  }
}
