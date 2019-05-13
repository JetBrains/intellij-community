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
package com.jetbrains.python;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.doctest.PyDocstringParserDefinition;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.List;

public class PyDocstringTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/doctests/";
  }

  public void testWelcome() {
    doTestLexer("  >>> foo()", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:STATEMENT_BREAK");
  }

  public void testDots() {
    doTestLexer(" >>> grouped == { 2:2,\n" +
                "  ...              3:3}", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:EQEQ", "Py:SPACE", "Py:LBRACE", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:COLON", "Py:INTEGER_LITERAL", "Py:COMMA", "Py:LINE_BREAK", "Py:DOT", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:COLON", "Py:INTEGER_LITERAL", "Py:RBRACE", "Py:STATEMENT_BREAK");
  }

  public void testComment() {  //PY-8505
    doTestLexer(" >>> if True:\n" +
                " ... #comm\n"+
                " ...   pass", "Py:SPACE", "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK");
  }
  public void testFunctionName() {
    doCompletionTest();
  }

  public void testClassName() {
    doCompletionTest();
  }

  public void doCompletionTest() {
    String inputDataFileName = getInputDataFileName(getTestName(true));
    String expectedResultFileName = getExpectedResultFileName(getTestName(true));
    myFixture.testCompletion(inputDataFileName, expectedResultFileName);
  }

  // util methods
  private static String getInputDataFileName(String testName) {
    return testName + ".docstring";
  }

  private static String getExpectedResultFileName(String testName) {
    return testName + ".expected.docstring";
  }

  public void testNoErrors() {
    doTestIndentation(false);
  }

  public void testHasErrors() {
    doTestIndentation(true);
  }

  private void doTestIndentation(boolean hasErrors) {
    String inputDataFileName = getTestName(true) + ".py";
    myFixture.configureByFile(inputDataFileName);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myFixture.getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement,TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    for (Pair<PsiElement,TextRange> pair : files) {
      assertEquals(hasErrors, PsiTreeUtil.hasErrorElements(pair.getFirst()));
    }
  }


  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PyDocstringParserDefinition().createLexer(myFixture.getProject());
    lexer.start(text);
    int idx = 0;
    while (lexer.getTokenType() != null) {
      if (idx >= expectedTokens.length) {
        StringBuilder remainingTokens = new StringBuilder("\"" + lexer.getTokenType().toString() + "\"");
        lexer.advance();
        while (lexer.getTokenType() != null) {
          remainingTokens.append(",");
          remainingTokens.append(" \"").append(lexer.getTokenType().toString()).append("\"");
          lexer.advance();
        }
        fail("Too many tokens. Following tokens: " + remainingTokens.toString());
      }
      String tokenName = lexer.getTokenType().toString();
      assertEquals("Token mismatch at position " + idx, expectedTokens[idx], tokenName);
      idx++;
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }
}
