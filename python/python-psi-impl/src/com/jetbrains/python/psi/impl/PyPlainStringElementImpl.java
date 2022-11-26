package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyPlainStringElementImpl extends LeafPsiElement implements PyPlainStringElement {
  public PyPlainStringElementImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @NotNull
  @Override
  public String getPrefix() {
    return PyStringLiteralCoreUtil.getPrefix(getText());
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
  public TextRange getContentRange() {
    return PyStringLiteralUtil.getContentRange(getText());
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
    final String text = getText();
    final String quote = getQuote();
    return text.length() >= getPrefixLength() +  quote.length() * 2 && text.endsWith(quote);
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
