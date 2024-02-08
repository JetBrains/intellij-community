// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;

public class PythonQuoteHandler extends BaseQuoteHandler {
  public PythonQuoteHandler() {
    super(PyTokenTypes.STRING_NODES, new char[]{'}', ']', ')', ',', ':', ';', ' ', '\t', '\n'});
  }

  @NotNull
  @Override
  protected TokenSet getOpeningQuotesTokens() {
    return TokenSet.orSet(super.getOpeningQuotesTokens(), TokenSet.create(PyTokenTypes.FSTRING_START));
  }

  @NotNull
  @Override
  protected TokenSet getClosingQuotesTokens() {
    return TokenSet.orSet(super.getClosingQuotesTokens(), TokenSet.create(PyTokenTypes.FSTRING_END));
  }

  @NotNull
  @Override
  protected TokenSet getLiteralContentTokens() {
    return TokenSet.orSet(super.getLiteralContentTokens(), TokenSet.create(PyTokenTypes.FSTRING_TEXT));
  }
}
