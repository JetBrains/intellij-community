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
  protected void processSpecialTokens() {
    super.processSpecialTokens();
    int tokenStart = getBaseTokenStart();
    if (getBaseTokenType() == null && addFinalBreak) {
      pushToken(PyTokenTypes.STATEMENT_BREAK, tokenStart, tokenStart);
      addFinalBreak = false;
    }
  }
}
