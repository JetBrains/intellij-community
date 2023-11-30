// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.embedding;

import com.intellij.lexer.DelegateLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A lexer which additionally providers information concerning whether a token
 * should be used in parsing (and be exposed to a parser) or be hidden to parser
 * @see MasqueradingPsiBuilderAdapter
 */
public abstract class MasqueradingLexer extends DelegateLexer {

  public MasqueradingLexer(@NotNull Lexer delegate) {
    super(delegate);
  }

  public abstract @Nullable IElementType getMasqueTokenType();

  public abstract @Nullable String getMasqueTokenText();

  public static class SmartDelegate extends MasqueradingLexer {

    public SmartDelegate(Lexer delegate) {
      super(delegate);
    }

    @Override
    public @Nullable IElementType getMasqueTokenType() {
      if (myDelegate instanceof MasqueradingLexer) {
        return ((MasqueradingLexer)myDelegate).getMasqueTokenType();
      }

      return myDelegate.getTokenType();
    }

    @Override
    public @Nullable String getMasqueTokenText() {
      if (myDelegate instanceof MasqueradingLexer) {
        return ((MasqueradingLexer)myDelegate).getMasqueTokenText();
      }

      return myDelegate.getTokenText();
    }
  }

}
