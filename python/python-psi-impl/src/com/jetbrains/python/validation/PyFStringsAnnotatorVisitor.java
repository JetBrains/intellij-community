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

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFormattedStringElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
final class PyFStringsAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  PyFStringsAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyFStringFragment(@NotNull PyFStringFragment node) {
    final PsiElement typeConversion = node.getTypeConversion();
    if (typeConversion != null) {
      final String conversionChar = typeConversion.getText().substring(1);
      if (conversionChar.isEmpty()) {
        report(typeConversion, PyPsiBundle.message("ANN.fstrings.missing.conversion.character"));
      }
      else if (conversionChar.length() > 1 || "sra".indexOf(conversionChar.charAt(0)) < 0) {
        report(typeConversion, PyPsiBundle.message("ANN.fstrings.illegal.conversion.character", conversionChar));
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
          reportCharacter(node, i, PyPsiBundle.message("ANN.fstrings.single.right.brace.not.allowed.inside.fstrings"));
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

  public void reportCharacter(@NotNull PsiElement element, int offset, @NotNull @InspectionMessage String message) {
    final int nodeStartOffset = element.getTextRange().getStartOffset();
    myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(TextRange.from(offset, 1).shiftRight(nodeStartOffset)).create();
  }

  public void report(@NotNull PsiElement element, @NotNull @InspectionMessage String error) {
    myHolder.newAnnotation(HighlightSeverity.ERROR, error).range(element).create();
  }
}