// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;

import java.io.Reader;

/**
 * @author yole
 */
public class PythonIndentingLexer extends PythonIndentingProcessor {
  public PythonIndentingLexer() {
    super(new _PythonLexer((Reader)null), TokenSet.EMPTY);
  }

  boolean addFinalBreak = true;
  @Override
  protected void processSpecialTokens() {
    super.processSpecialTokens();
    int tokenStart = getBaseTokenStart();
    if (getBaseTokenType() == null && addFinalBreak) {
      pushToken(PyTokenTypes.STATEMENT_BREAK, tokenStart, tokenStart);
      addFinalBreak = false;
    }
  }
}
