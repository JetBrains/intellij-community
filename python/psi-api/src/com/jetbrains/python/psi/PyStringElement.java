package com.jetbrains.python.psi;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A common interface containing utility methods shared among both plain string literals
 * (i.e. those that don't have "f" prefix) and formatted literals (f-strings).
 *
 * @see PyPlainStringElement
 * @see PyFormattedStringElement
 */
public interface PyStringElement extends PsiElement {

  /**
   * Returns string prefix, e.g. "UR", "b", "f", etc.
   *
   * @see #getPrefixLength()
   */
  @NotNull
  String getPrefix();

  /**
   * Returns the length of the prefix.
   *
   * @see #getPrefix()
   */
  int getPrefixLength();

  /**
   * Returns <em>relative</em> range of this element actual content that contributes to {@link StringLiteralExpression#getStringValue()},
   * i.e. excluding prefix and quotes.
   *
   * @see #getContent()
   */
  @NotNull
  TextRange getContentRange();

  /**
   * Returns the content of the string node between quotes
   * 
   * @see #getContentRange() 
   */
  @NotNull
  String getContent();

  /**
   * Has the same meaning as {@link PyStringLiteralExpression#getDecodedFragments()} but applied to individual string literals
   * composing it. 
   * 
   * @see PyStringLiteralExpression#getDecodedFragments() 
   */
  @NotNull
  List<Pair<TextRange, String>> getDecodedFragments();

  /**
   * Returns starting quotes of the string literal, regardless of whether it's properly terminated or not.
   *
   * @see #isTerminated()
   */
  @NotNull
  String getQuote();

  /**
   * Returns whether this string literal is enclosed in triple quotes and thus can contain unescaped line breaks.
   */
  boolean isTripleQuoted();

  /**
   * Returns whether this string literal is properly terminated with the same type of quotes it begins with.
   */
  boolean isTerminated();

  /**
   * Returns whether this string literal contains "u" or "U" prefix.
   * <p>
   * It's unrelated to which type the corresponding string literal expression actually has at runtime.
   */
  boolean isUnicode();

  /**
   * Returns whether this string literal contains "r" or "R" prefix.
   */
  boolean isRaw();

  /**
   * Returns whether this string literal contains "b" or "B" prefix.
   */
  boolean isBytes();

  /**
   * Returns whether this string literal contains "f" or "F" prefix.
   * <p>
   * It can be used as a counterpart for {@code this isinstance PyFormattedStringElement}, since only this implementation
   * is allowed to have such prefix.
   */
  boolean isFormatted();
}
