// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.fixtures;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.PlatformLiteFixture;
import com.jetbrains.python.PythonDialectsTokenSetContributor;
import com.jetbrains.python.PythonTokenSetContributor;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.Objects;


public abstract class PyLexerTestCase extends PlatformLiteFixture {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
  }

  public static void doLexerTest(String text, Lexer lexer, String... expectedTokens) {
    doLexerTest(text, lexer, false, expectedTokens);
  }

  public static void doLexerTest(String text, Lexer lexer, boolean checkTokenText, String... expectedTokens) {
    lexer.start(text);
    List<String> actualTokens = StreamEx.generate(() -> {
        IElementType nextTokenType = lexer.getTokenType();
        if (nextTokenType == null) return null;
        String nextToken = checkTokenText ? lexer.getTokenText() : nextTokenType.toString();
        lexer.advance();
        return nextToken;
      })
      .takeWhile(Objects::nonNull)
      .toList();
    String expectedTokensInCode = StringUtil.join(actualTokens, t -> '"' + t + '"', ", ");
    assertEquals("Token mismatch. Actual values: " + expectedTokensInCode, List.of(expectedTokens), actualTokens);
  }

}
