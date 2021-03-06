/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFStringFragmentFormatPart;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class FStringsAnnotator extends PyAnnotator {

  @Override
  public void visitPyFStringFragment(@NotNull PyFStringFragment node) {
    final List<PyFStringFragment> enclosingFragments = PsiTreeUtil.collectParents(node, PyFStringFragment.class, false,
                                                                                  PyStringLiteralExpression.class::isInstance);
    if (enclosingFragments.size() > 1) {
      report(node, PyBundle.message("ANN.fstrings.expression.fragment.inside.fstring.nested.too.deeply"));
    }
    final PsiElement typeConversion = node.getTypeConversion();
    if (typeConversion != null) {
      final String conversionChar = typeConversion.getText().substring(1);
      if (conversionChar.isEmpty()) {
        report(typeConversion, PyBundle.message("ANN.fstrings.missing.conversion.character"));
      }
      else if (conversionChar.length() > 1 || "sra".indexOf(conversionChar.charAt(0)) < 0) {
        report(typeConversion, PyBundle.message("ANN.fstrings.illegal.conversion.character", conversionChar));
      }
    }

    final boolean topLevel = PsiTreeUtil.getParentOfType(node, PyFStringFragment.class, true) == null;
    if (topLevel) {
      final List<PyFStringFragment> fragments = Lists.newArrayList(node);
      final PyFStringFragmentFormatPart formatPart = node.getFormatPart();
      if (formatPart != null) {
        fragments.addAll(formatPart.getFragments());
      }
      for (PyFStringFragment fragment : fragments) {
        final String wholeNodeText = fragment.getText();
        final TextRange range = fragment.getExpressionContentRange();
        for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
          if (wholeNodeText.charAt(i) == '\\') {
            reportCharacter(fragment, i, PyBundle.message("ANN.fstrings.expression.fragments.cannot.include.backslashes"));
          }
        }
      }
    }
  }

  @Override
  public void visitPyFormattedStringElement(@NotNull PyFormattedStringElement node) {
    final String wholeNodeText = node.getText();
    for (TextRange textRange : node.getLiteralPartRanges()) {
      int i = textRange.getStartOffset();
      while (i < textRange.getEndOffset()) {
        final int nextOffset = skipNamedUnicodeEscape(wholeNodeText, i, textRange.getEndOffset());
        if (i != nextOffset) {
          i = nextOffset;
          continue;
        }
        final char c = wholeNodeText.charAt(i);
        if (c == '}') {
          if (i + 1 < textRange.getEndOffset() && wholeNodeText.charAt(i + 1) == '}') {
            i += 2;
            continue;
          }
          reportCharacter(node, i, PyBundle.message("ANN.fstrings.single.right.brace.not.allowed.inside.fstrings"));
        }
        i++;
      }
    }
  }

  private static int skipNamedUnicodeEscape(@NotNull String nodeText, int offset, int endOffset) {
    if (StringUtil.startsWith(nodeText, offset, "\\N{")) {
      final int rightBraceOffset = nodeText.indexOf('}', offset + 3);
      return rightBraceOffset < 0 ? endOffset : rightBraceOffset + 1;
    }
    return offset;
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    final boolean insideFragment = PsiTreeUtil.getParentOfType(comment, PyFStringFragment.class) != null;
    if (insideFragment) {
      report(comment, PyBundle.message("ANN.fstrings.expression.fragments.cannot.include.line.comments"));
    }
  }

  public void reportCharacter(@NotNull PsiElement element, int offset, @NotNull @InspectionMessage String message) {
    final int nodeStartOffset = element.getTextRange().getStartOffset();
    getHolder().newAnnotation(HighlightSeverity.ERROR, message).range(TextRange.from(offset, 1).shiftRight(nodeStartOffset)).create();
  }

  public void report(@NotNull PsiElement element, @NotNull @InspectionMessage String error) {
    getHolder().newAnnotation(HighlightSeverity.ERROR, error).range(element).create();
  }
}
