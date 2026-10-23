// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstFStringFragment;
import com.jetbrains.python.ast.PyAstFormattedStringElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
final class PyFStringsAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  PyFStringsAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyFStringFragment(@NotNull PyAstFStringFragment node) {
    final PsiElement typeConversion = node.getTypeConversion();
    if (typeConversion != null) {
      final String conversionChar = typeConversion.getText().substring(1);
      if (conversionChar.isEmpty()) {
        report(typeConversion, PySyntaxCoreBundle.message("ANN.fstrings.missing.conversion.character"));
      }
      else if (conversionChar.length() > 1 || "sra".indexOf(conversionChar.charAt(0)) < 0) {
        report(typeConversion, PySyntaxCoreBundle.message("ANN.fstrings.illegal.conversion.character", conversionChar));
      }
    }
  }

  @Override
  public void visitPyFormattedStringElement(@NotNull PyAstFormattedStringElement node) {
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
          reportCharacter(node, i, PySyntaxCoreBundle.message("ANN.fstrings.single.right.brace.not.allowed.inside.fstrings"));
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