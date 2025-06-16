// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.*;

/**
 * Represents an f-string, a special kind of interpolated string literal introduced in Python 3.6.
 * 
 * Unlike {@link PyAstPlainStringElement} these elements are composite and consist of several kinds of tokens
 * and normal PSI trees for embedded expressions.
 * 
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_START
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_TEXT
 * @see PyAstFStringFragment
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_END
 */
@ApiStatus.Experimental
public interface PyAstFormattedStringElement extends PyAstStringElement, PyAstElement {
  /**
   * Returns a list of replacement fields containing expressions which values should be embedded into this literal content.
   * <p>
   * Note that only top-level fields are included in the result. To access optional fragments that might appear in
   * format specifier of a another fragment one should use {@link PyAstFStringFragment#getFormatPart()} and
   * {@link PyAstFStringFragmentFormatPart#getFragments()}.
   */
  default @NotNull List<? extends PyAstFStringFragment> getFragments() {
    return findChildrenByType(this, PyElementTypes.FSTRING_FRAGMENT);
  }

  /**
   * Returns ranges of literal parts of an f-string, e.g. all other parts of an f-string content that don't belong to
   * expression fragments.
   * <p>
   * These ranges don't include literal format specifier parts of fragments and should be completely
   * covered by {@link PyAstStringElement#getContentRange()}.
   * <p>
   * For instance, for the following f-string:
   * <p>
   * <pre>{@code
   *   f'foo{bar:format}baz'
   * }</pre>
   * this method will return ranges {@code (2, 5)} and {@code (17, 20)}.
   */
  default @NotNull List<TextRange> getLiteralPartRanges() {
    final List<PsiElement> textTokens = findChildrenByType(this, PyTokenTypes.FSTRING_TEXT_TOKENS);
    return ContainerUtil.map(textTokens, PsiElement::getTextRangeInParent);
  }

  @Override
  default @NotNull TextRange getContentRange() {
    final TextRange textRange = getTextRange();
    final int startOffset = textRange.getStartOffset();
    final int endOffset = textRange.getEndOffset();

    final PsiElement startToken = findChildByTypeNotNull(this, PyTokenTypes.FSTRING_START);
    final PsiElement endToken = findChildByType(this, PyTokenTypes.FSTRING_END);
    final TextRange absoluteRange = TextRange.create(startToken.getTextRange().getEndOffset(),
                                                     endToken != null ? endToken.getTextRange().getStartOffset() : endOffset);
    return absoluteRange.shiftLeft(startOffset);
  }

  @Override
  default @NotNull List<Pair<TextRange, String>> getDecodedFragments() {
    final ArrayList<Pair<TextRange, String>> result = new ArrayList<>();
    final PyStringLiteralDecoder decoder = new PyStringLiteralDecoder(this);
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      final IElementType childType = child.getNode().getElementType();
      final TextRange relChildRange = child.getTextRangeInParent();
      if (PyTokenTypes.FSTRING_TEXT_TOKENS.contains(childType)) {
        result.addAll(decoder.decodeRange(relChildRange));
      }
      else if (childType == PyElementTypes.FSTRING_FRAGMENT) {
        // There shouldn't be any escaping inside interpolated parts
        result.add(Pair.create(relChildRange, child.getText()));
      }
    }
    return result;
  }

  @Override
  default @NotNull String getQuote() {
    final PsiElement start = findChildByTypeNotNull(this, PyTokenTypes.FSTRING_START);
    return start.getText().substring(getPrefixLength());
  }

  @Override
  default boolean isTerminated() {
    return findChildByType(this, PyTokenTypes.FSTRING_END) != null;
  }

  @Override
  default boolean isFormatted() {
    return StringUtil.containsIgnoreCase(getPrefix(), "f");
  }

  @Override
  default boolean isTemplate() {
    return StringUtil.containsIgnoreCase(getPrefix(), "t");
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyFormattedStringElement(this);
  }
}
