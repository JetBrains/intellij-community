// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Looks for well-formedness of string constants.
 */
public final class PyStringLiteralQuotesAnnotator extends PyAnnotatorBase {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull PyAnnotationHolder holder) {
    element.accept(new MyVisitor(holder));
  }

  private static class MyVisitor extends PyElementVisitor {
    private final @NotNull PyAnnotationHolder myHolder;

    private MyVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

    private static final String TRIPLE_QUOTES = "\"\"\"";
    private static final String TRIPLE_APOS = "'''";

    @Override
    public void visitPyStringLiteralExpression(final @NotNull PyStringLiteralExpression node) {
      final List<ASTNode> stringNodes = node.getStringNodes();
      for (ASTNode stringNode : stringNodes) {
        // TODO Migrate to newer PyStringElement API
        if (stringNode.getElementType() == PyElementTypes.FSTRING_NODE) {
          continue;
        }
        final String nodeText = PyPsiUtils.getElementTextWithoutHostEscaping(stringNode.getPsi());
        final int index = PyStringLiteralUtil.getPrefixLength(nodeText);
        final String unprefixed = nodeText.substring(index);
        final boolean foundError;
        if (StringUtil.startsWith(unprefixed, TRIPLE_QUOTES)) {
          foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_QUOTES);
        }
        else if (StringUtil.startsWith(unprefixed, TRIPLE_APOS)) {
          foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_APOS);
        }
        else {
          foundError = checkQuotedString(stringNode, unprefixed);
        }
        if (foundError) {
          break;
        }
      }
    }

    private boolean checkQuotedString(@NotNull ASTNode stringNode, @NotNull String nodeText) {
      final char firstQuote = nodeText.charAt(0);
      final char lastChar = nodeText.charAt(nodeText.length() - 1);
      int precedingBackslashCount = 0;
      for (int i = nodeText.length() - 2; i >= 0; i--) {
        if (nodeText.charAt(i) == '\\') {
          precedingBackslashCount++;
        }
        else {
          break;
        }
      }
      if (nodeText.length() == 1 || lastChar != firstQuote || precedingBackslashCount % 2 != 0) {
        myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.missing.closing.quote", firstQuote)).range(stringNode)
          .create();
        return true;
      }
      return false;
    }

    private boolean checkTripleQuotedString(@NotNull ASTNode stringNode, @NotNull String text, @NotNull String quotes) {
      if (text.length() < 6 || !text.endsWith(quotes)) {
        int startOffset = StringUtil.trimTrailing(stringNode.getText()).lastIndexOf('\n');
        if (startOffset < 0) {
          startOffset = stringNode.getTextRange().getStartOffset();
        }
        else {
          startOffset = stringNode.getTextRange().getStartOffset() + startOffset + 1;
        }
        final TextRange highlightRange = new TextRange(startOffset, stringNode.getTextRange().getEndOffset());
        myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.missing.closing.triple.quotes")).range(highlightRange)
          .create();
        return true;
      }
      return false;
    }
  }
}