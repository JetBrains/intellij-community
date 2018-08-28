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
    return PyStringLiteralUtil.getPrefixLength(getText());
  }

  @NotNull
  @Override
  public String getContent() {
    return getContentRange().substring(getText());
  }

  @NotNull
  @Override
  public List<Pair<TextRange, String>> getDecodedFragments() {
    return new PyStringLiteralDecoder(this).decodeContent();
  }

  @NotNull
  @Override
  public String getTextWithoutPrefix() {
    return getText().substring(getPrefixLength());
  }

  @NotNull
  @Override
  public TextRange getContentRange() {
    return PyStringLiteralUtil.getContentRange(getText());
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
    return getText().substring(getPrefixLength(), getContentRange().getStartOffset());
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

  @Override
  public boolean isUnicode() {
    return StringUtil.containsIgnoreCase(getPrefix(), "u");
  }

  @Override
  public boolean isRaw() {
    return StringUtil.containsIgnoreCase(getPrefix(), "r");
  }

  @Override
  public boolean isBytes() {
    return StringUtil.containsIgnoreCase(getPrefix(), "b");
  }

  @Override
  public final boolean isFormatted() {
    return false;
  }
}
