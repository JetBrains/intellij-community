package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.PyLiteralStringNode;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * @author Mikhail Golubev
 */
public class PyLiteralStringNodeImpl extends LeafPsiElement implements PyLiteralStringNode {
  public PyLiteralStringNodeImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @NotNull
  @Override
  public String getPrefix() {
    return PyStringLiteralUtil.getPrefix(getText());
  }

  @Override
  public int getPrefixLength() {
    return PyStringLiteralUtil.getPrefixEndOffset(getText(), 0);
  }

  @NotNull
  @Override
  public String getContent() {
    return getContentRange().substring(getText());
  }

  @NotNull
  @Override
  public List<Pair<TextRange, String>> getDecodedFragments() {
    final PyStringLiteralDecoder decoder = new PyStringLiteralDecoder(this);
    decoder.decodeContent();
    return decoder.getResult();
  }

  @NotNull
  @Override
  public String getTextWithoutPrefix() {
    return getText().substring(getPrefixLength());
  }

  @NotNull
  @Override
  public TextRange getContentRange() {
    return PyStringLiteralExpressionImpl.getNodeTextRange(getText());
  }

  @NotNull
  @Override
  public TextRange getAbsoluteContentRange() {
    return getContentRange().shiftRight(getStartOffset());
  }

  @Override
  public char getQuoteChar() {
    return getQuote().charAt(0);
  }

  @NotNull
  @Override
  public String getQuote() {
    final Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(getText());
    assert quotes != null;
    return quotes.getFirst();
  }

  @Override
  public boolean isTripleQuoted() {
    return getQuote().length() == 3;
  }

  @Override
  public boolean isTerminated() {
    final String unprefixed = getTextWithoutPrefix();
    final String quote = getQuote();
    return unprefixed.length() >= quote.length() * 2 && unprefixed.endsWith(quote);
  }

  @NotNull
  @Override
  public Set<Modifier> getModifiers() {
    final EnumSet<Modifier> result = EnumSet.noneOf(Modifier.class);
    if (isUnicode()) {
      result.add(Modifier.UNICODE);
    }
    if (isBytes()) {
      result.add(Modifier.BYTES);
    }
    if (isRaw()) {
      result.add(Modifier.RAW);
    }
    if (isFormatted()) {
      result.add(Modifier.FORMATTED);
    }
    return result;
  }

  /**
   * @return true if given string node contains "u" or "U" prefix
   */
  @Override
  public boolean isUnicode() {
    return StringUtil.containsIgnoreCase(getPrefix(), "u");
  }

  /**
   * @return true if given string node contains "r" or "R" prefix
   */
  @Override
  public boolean isRaw() {
    return StringUtil.containsIgnoreCase(getPrefix(), "r");
  }

  /**
   * @return true if given string node contains "b" or "B" prefix
   */
  @Override
  public boolean isBytes() {
    return StringUtil.containsIgnoreCase(getPrefix(), "b");
  }

  @Override
  public boolean isFormatted() {
    return StringUtil.containsIgnoreCase(getPrefix(), "f");
  }
}
