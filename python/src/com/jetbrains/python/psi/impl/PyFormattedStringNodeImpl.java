// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFormattedStringNode;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class PyFormattedStringNodeImpl extends PyElementImpl implements PyFormattedStringNode {

  public PyFormattedStringNodeImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  @Override
  public List<PyFStringFragment> getFragments() {
    return findChildrenByType(PyElementTypes.FSTRING_FRAGMENT);
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
  public String getTextWithoutPrefix() {
    return null;
  }

  @NotNull
  @Override
  public TextRange getContentRange() {
    return null;
  }

  @NotNull
  @Override
  public TextRange getAbsoluteContentRange() {
    return null;
  }

  @NotNull
  @Override
  public String getContent() {
    return null;
  }

  @NotNull
  @Override
  public List<Pair<TextRange, String>> getDecodedFragments() {
    return null;
  }

  @NotNull
  @Override
  public String getQuote() {
    return null;
  }

  @Override
  public char getQuoteChar() {
    return 0;
  }

  @Override
  public boolean isTripleQuoted() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @NotNull
  @Override
  public Set<Modifier> getModifiers() {
    return null;
  }

  @Override
  public boolean isUnicode() {
    return false;
  }

  @Override
  public boolean isRaw() {
    return false;
  }

  @Override
  public boolean isBytes() {
    return false;
  }

  @Override
  public final boolean isFormatted() {
    return true;
  }
}
