package com.jetbrains.python.ast;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a string literal which content in constant (without any interpolation taking place).
 * Namely, these are all kinds of string literals in Python except for f-strings and they map directly
 * to underlying tokens. The following are all examples of such elements:
 *
 * <ul>
 * <li>{@code r'foo\42'}</li>
 * <li><pre><code>
 * b"""\
 * multi
 * line
 * bytes"""</code><pre/></li>
 * <li>{@code '\u0041 \x41 \N{LATIN CAPITAL LETTER A}'}</li>
 * </ul>
 */
@ApiStatus.Experimental
public interface PyAstPlainStringElement extends PyAstStringElement {
  @Override
  default @NotNull List<Pair<TextRange, String>> getDecodedFragments() {
    return new PyStringLiteralDecoder(this).decodeContent();
  }

  @Override
  default @NotNull TextRange getContentRange() {
    return PyStringLiteralUtil.getContentRange(getText());
  }

  @Override
  default @NotNull String getQuote() {
    return getText().substring(getPrefixLength(), getContentRange().getStartOffset());
  }

  @Override
  default boolean isTerminated() {
    final String text = getText();
    final String quote = getQuote();
    return text.length() >= getPrefixLength() +  quote.length() * 2 && text.endsWith(quote);
  }

  @Override
  default boolean isFormatted() {
    return false;
  }

  @Override
  default boolean isTemplate() {
    return false;
  }
}
