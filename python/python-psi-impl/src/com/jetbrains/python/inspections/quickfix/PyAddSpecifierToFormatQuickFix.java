// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.PyStringFormatParser.filterSubstitutions;
import static com.jetbrains.python.PyStringFormatParser.parsePercentFormat;

public class PyAddSpecifierToFormatQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.add.specifier");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyBinaryExpression expression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
    if (expression == null) return;
    PyExpression rightExpression = expression.getRightExpression();
    if (rightExpression instanceof PyParenthesizedExpression) {
      rightExpression = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
    }
    if (rightExpression == null) return;

    final PsiFile file = element.getContainingFile();
    final Document document = file.getViewProvider().getDocument();
    if (document == null) return;
    final int offset = element.getTextOffset();
    final TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file);

    final PyClassType strType = PyBuiltinCache.getInstance(element).getStrType();
    final PyClassType floatType = PyBuiltinCache.getInstance(element).getFloatType();
    final PyClassType intType = PyBuiltinCache.getInstance(element).getIntType();

    final PyExpression leftExpression = expression.getLeftExpression();
    if (leftExpression instanceof PyStringLiteralExpression) {
      final List<PyStringFormatParser.SubstitutionChunk> chunks =
        filterSubstitutions(parsePercentFormat(leftExpression.getText()));
      PyExpression[] elements;
      if (rightExpression instanceof PyTupleExpression) {
        elements = ((PyTupleExpression)rightExpression).getElements();
      }
      else {
        elements = new PyExpression[]{rightExpression};
      }

      int shift = 1;
      for (int i = 0; i < chunks.size(); i++) {
        final PyStringFormatParser.PercentSubstitutionChunk chunk = PyUtil.as(chunks.get(i), PyStringFormatParser.PercentSubstitutionChunk.class);
        if (chunk != null) {
          if (elements.length <= i) return;
          final PyType type = context.getType(elements[i]);
          final char conversionType = chunk.getConversionType();
          if (conversionType == '\u0000') {
            final int insertOffset = offset + chunk.getStartIndex() + shift;
            if (insertOffset > leftExpression.getTextRange().getEndOffset()) return;
            if (PyTypeChecker.match(strType, type, context)) {
              document.insertString(insertOffset, "s");
              shift += 1;
            }
            if (PyTypeChecker.match(intType, type, context) || PyTypeChecker.match(floatType, type, context)) {
              document.insertString(insertOffset, "d");
              shift += 1;
            }
          }
        }
      }
    }
  }
}
