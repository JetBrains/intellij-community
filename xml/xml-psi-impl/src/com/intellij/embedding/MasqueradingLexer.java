/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.embedding;

import com.intellij.lexer.DelegateLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A lexer which additionally providers information concerning whether a token
 * should be used in parsing (and be exposed to a parser) or be hidden to parser
 * @see com.intellij.embedding.MasqueradingPsiBuilderAdapter
 */
public abstract class MasqueradingLexer extends DelegateLexer {

  public MasqueradingLexer(@NotNull Lexer delegate) {
    super(delegate);
  }

  @Nullable
  public abstract IElementType getMasqueTokenType();

  @Nullable
  public abstract String getMasqueTokenText();

  public static class SmartDelegate extends MasqueradingLexer {

    public SmartDelegate(Lexer delegate) {
      super(delegate);
    }

    @Nullable
    public IElementType getMasqueTokenType() {
      if (myDelegate instanceof MasqueradingLexer) {
        return ((MasqueradingLexer)myDelegate).getMasqueTokenType();
      }

      return myDelegate.getTokenType();
    }

    @Nullable
    public String getMasqueTokenText() {
      if (myDelegate instanceof MasqueradingLexer) {
        return ((MasqueradingLexer)myDelegate).getMasqueTokenText();
      }

      return myDelegate.getTokenText();
    }
  }

}
