// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixture;
import com.intellij.sh.ShTestData;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixtures;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixtureKt.lexerFixture;

@TestFixtures
public class ShOldLexerVersion3Test {
  private final TestFixture<LexerTestFixture> lexer =
    lexerFixture(ShTestData.getTestDataPath() + "/oldLexer/v3", () -> new ShLexer());

  @Test
  public void testSimpleDefTokenization() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testVariables() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testArrayVariables() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testArrayWithString() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testSquareBracketArithmeticExpr() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testArithmeticExpr() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testLetExpressions() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testShebang() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIdentifier() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testStrings() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testSubshellString() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testSubshellSubstring() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testWords() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testInternalCommands() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testExpressions() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testSubshell() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testNumber() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testFunction() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testVariable() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testRedirect1() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testConditional() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testBracket() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testParameterSubstitution() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testWeirdStuff1() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testCaseWhitespacePattern() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testNestedCase() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testBackquote1() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testCasePattern() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testAssignmentList() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testEval() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testNestedStatements() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testV4Lexing() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testParamExpansionNested() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testParamExpansion() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testArithmeticLiterals() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testReadCommand() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testUmlaut() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testSubshellExpr() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue201() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testHeredoc() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testMultilineHeredoc() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue118() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue125() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue199() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue242() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue246() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue266() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue270() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue272() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue300() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue303() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue308() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue89() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue320() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue325() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue327() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue330() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue330Var() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue341() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue343() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue354() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue389() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testTrapLexing() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testEvalLexing() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue376() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue367() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue418() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testHereString() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testUnicode() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testLineContinuation() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue358() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue426() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue431() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue419() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue401() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue457() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue458() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue469() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue474() {
    lexer.get().doFileTest("sh");
  }

  @Test
  public void testIssue505() {
    lexer.get().doFileTest("sh");
  }
}
