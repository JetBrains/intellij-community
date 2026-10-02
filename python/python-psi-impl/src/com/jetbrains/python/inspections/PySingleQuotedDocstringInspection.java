// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.ConvertDocstringQuickFix;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * <p>
 * Inspection to detect docstrings not using triple double-quoted string
 */
@ApiStatus.Internal
public final class PySingleQuotedDocstringInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyStringLiteralExpression(final @NotNull PyStringLiteralExpression string) {
      String stringText = string.getText();
      int length = PyStringLiteralUtil.getPrefixLength(stringText);
      stringText = stringText.substring(length);
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string) {
          if (!stringText.startsWith("\"\"\"") && !stringText.endsWith("\"\"\"")) {
            ProblemsHolder holder = getHolder();
            if (holder != null) {
              int quoteCount = 1;
              if (stringText.startsWith("'''") && stringText.endsWith("'''")) {
                quoteCount = 3;
              }
              TextRange trStart = new TextRange(length, length + quoteCount);
              TextRange trEnd = new TextRange(stringText.length() + length - quoteCount,
                                              stringText.length() + length);
              if (string.getStringValue().isEmpty()) {
                holder.registerProblem(string, PyPsiBundle.message("INSP.message.single.quoted.docstring"),
                                       new ConvertDocstringQuickFix());
              }
              else {
                holder.registerProblem(string, trStart,
                                       PyPsiBundle.message("INSP.message.single.quoted.docstring"), new ConvertDocstringQuickFix());
                holder.registerProblem(string, trEnd,
                                       PyPsiBundle.message("INSP.message.single.quoted.docstring"), new ConvertDocstringQuickFix());
              }
            }
          }
        }
      }
    }
  }
}
