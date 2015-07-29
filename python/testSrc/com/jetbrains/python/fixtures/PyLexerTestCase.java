/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.fixtures;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.PlatformLiteFixture;
import com.jetbrains.python.PythonDialectsTokenSetContributor;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonTokenSetContributor;

/**
 * @author yole
 */
public abstract class PyLexerTestCase extends PlatformLiteFixture {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    PythonDialectsTokenSetProvider.reset();
  }

  public static void doLexerTest(String text, Lexer lexer, String... expectedTokens) {
    doLexerTest(text, lexer, false, expectedTokens);
  }

  public static void doLexerTest(String text,
                                 Lexer lexer,
                                 boolean checkTokenText,
                                 String... expectedTokens) {
    lexer.start(text);
    int idx = 0;
    int tokenPos = 0;
    while (lexer.getTokenType() != null) {
      if (idx >= expectedTokens.length) {
        StringBuilder remainingTokens = new StringBuilder("\"" + lexer.getTokenType().toString() + "\"");
        lexer.advance();
        while (lexer.getTokenType() != null) {
          remainingTokens.append(",");
          remainingTokens.append(" \"").append(checkTokenText ? lexer.getTokenText() : lexer.getTokenType().toString()).append("\"");
          lexer.advance();
        }
        fail("Too many tokens. Following tokens: " + remainingTokens.toString());
      }
      assertEquals("Token offset mismatch at position " + idx, tokenPos, lexer.getTokenStart());
      String tokenName = checkTokenText ? lexer.getTokenText() : lexer.getTokenType().toString();
      assertEquals("Token mismatch at position " + idx, expectedTokens[idx], tokenName);
      idx++;
      tokenPos = lexer.getTokenEnd();
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }
}
