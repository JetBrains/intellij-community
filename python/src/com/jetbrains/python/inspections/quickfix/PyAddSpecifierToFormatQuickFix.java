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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.inspections.PyStringFormatParser.filterSubstitutions;
import static com.jetbrains.python.inspections.PyStringFormatParser.parsePercentFormat;

public class PyAddSpecifierToFormatQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.specifier");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyBinaryExpression expression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
    if (expression == null) return;
    PyExpression rightExpression = expression.getRightExpression();
    if (rightExpression instanceof PyParenthesizedExpression) {
      rightExpression = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
    }
    if (rightExpression == null) return;

    final PsiFile file = element.getContainingFile();
    final Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    if (document == null) return;
    final int offset = element.getTextOffset();
    final TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file);

    final PyClassType strType = PyBuiltinCache.getInstance(element).getStrType();
    final PyClassType floatType = PyBuiltinCache.getInstance(element).getFloatType();
    final PyClassType intType = PyBuiltinCache.getInstance(element).getIntType();

    final PyExpression leftExpression = expression.getLeftExpression();
    if (leftExpression instanceof PyStringLiteralExpression) {
      final List<PyStringFormatParser.SubstitutionChunk> chunks =
        filterSubstitutions(parsePercentFormat(((PyStringLiteralExpression)leftExpression).getStringValue()));
      PyExpression[] elements;
      if (rightExpression instanceof PyTupleExpression) {
        elements = ((PyTupleExpression)rightExpression).getElements();
      }
      else {
        elements = new PyExpression[]{rightExpression};
      }

      int shift = 2;
      for (int i = 0; i < chunks.size(); i++) {
        final PyStringFormatParser.SubstitutionChunk chunk = chunks.get(i);
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
