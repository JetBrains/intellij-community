// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;
import static com.jetbrains.python.ast.PyAstElementKt.findChildByType;

/**
 * Represents a replacement field that can appear either inside an f-string literal itself or inside
 * a format specifier of another replacement field, e.g. {@code {  expr  !s:{width}}} and {@code {width}}
 * parts of the f-string {@code f'{  expr  !s:{width}}'}.
 * <p>
 * Note that opening and closing braces are represented with dedicated
 * {@link com.jetbrains.python.PyTokenTypes#FSTRING_FRAGMENT_START} and {@link com.jetbrains.python.PyTokenTypes#FSTRING_FRAGMENT_END}
 * tokens instead of regular types for curly braces.
 */
@ApiStatus.Experimental
public interface PyAstFStringFragment extends PyAstElement {

  /**
   * Returns the primary expression of this fragment, i.e. the one that appears after the opening brace and
   * before a type conversion, a format specifier or a closing curly brace. Nested expressions inside
   * fragments of format specifier can be accessed as primary expressions of nested fragments retrieved with
   * {@code getFormatPart().getFragments()}.
   */
  @Nullable
  default PyAstExpression getExpression() {
    return findChildByClass(this, PyAstExpression.class);
  }

  /**
   * Returns a text range that covers the primary expression together with any preceding and trailing whitespaces.
   * <p>
   * For instance, for the fragment {@code {  expr  !s:{width}}} it's {@code (1, 9)}, though the range {@link #getExpression()}
   * covers only {@code (3, 7)}.
   */
  @NotNull
  default TextRange getExpressionContentRange() {
    final PsiElement endAnchor = ObjectUtils.coalesce(getTypeConversion(), getFormatPart(), getClosingBrace());
    return TextRange.create(1, endAnchor != null ? endAnchor.getStartOffsetInParent() : getTextLength());
  }

  /**
   * Returns an optional type conversion part of a replacement field, presumably either "!r", "!s" or "!a".
   * <p>
   * For instance, for the fragment {@code {  expr  !s:{width}}} it's {@code !s}.
   */
  @Nullable
  default PsiElement getTypeConversion() {
    return findChildByType(this, PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION);
  }

  /**
   * Returns an optional format specifier part of a replacement field. It always starts with a colon and spans up to
   * the closing brace of the fragment itself.
   * <p>
   * For instance, for the fragment {@code {  expr  !s:{width}}} it's {@code !s:{width}}.
   */
  @Nullable
  default PyAstFStringFragmentFormatPart getFormatPart() {
    return findChildByClass(this, PyAstFStringFragmentFormatPart.class);
  }

  @Nullable
  default PsiElement getClosingBrace() {
    return findChildByType(this, PyTokenTypes.RBRACE);
  }
}
