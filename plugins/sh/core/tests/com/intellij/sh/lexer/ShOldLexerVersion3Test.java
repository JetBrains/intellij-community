// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

public class ShOldLexerVersion3Test extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new ShLexer();
  }

  @Override
  protected String getDirPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/core/testData/oldLexer/v3";
  }

  @NotNull
  @Override
  protected String getPathToTestDataFile(String extension) {
    return getDirPath() + "/" + getTestName(true) + extension;
  }

  public void testSimpleDefTokenization() {
    doFileTest("sh");
  }

  public void testVariables() {
    doFileTest("sh");
  }

  public void testArrayVariables() {
    doFileTest("sh");
  }

  public void testArrayWithString() {
    doFileTest("sh");
  }

  public void testSquareBracketArithmeticExpr() {
    doFileTest("sh");
  }
  
  public void testArithmeticExpr() {
    doFileTest("sh");
  }
  
  public void testLetExpressions() {
    doFileTest("sh");
  }

  public void testShebang() {
    doFileTest("sh");
  }

  public void testIdentifier() {
    doFileTest("sh");
  }
  
  public void testStrings() {
    doFileTest("sh");
  }

  public void testSubshellString() {
    doFileTest("sh");
  }

  public void testSubshellSubstring() {
    doFileTest("sh");
  }

  public void testWords() {
    doFileTest("sh");
  }

  public void testInternalCommands() {
    doFileTest("sh");
  }

  public void testExpressions() {
    doFileTest("sh");
  }

  public void testSubshell() {
    doFileTest("sh");
  }

  public void testNumber() {
    doFileTest("sh");
  }

  public void testFunction() {
    doFileTest("sh");
  }

  public void testVariable() {
    doFileTest("sh");
  }
  
  public void testRedirect1() {
    doFileTest("sh");
  }

  public void testConditional() {
    doFileTest("sh");
  }

  public void testBracket() {
    doFileTest("sh");
  }

  public void testParameterSubstitution() {
    doFileTest("sh");
  }
  
  public void testWeirdStuff1() {
    doFileTest("sh");
  }
  
  public void testCaseWhitespacePattern() {
    doFileTest("sh");
  }
  
  public void testNestedCase() {
    doFileTest("sh");
  }
  
  public void testBackquote1() {
    doFileTest("sh");
  }
  
  public void testCasePattern() {
    doFileTest("sh");
  }
  
  public void testAssignmentList() {
    doFileTest("sh");
  }

  public void testEval() {
    doFileTest("sh");
  }

  public void testNestedStatements() {
    doFileTest("sh");
  }
  
  public void testV4Lexing() {
    doFileTest("sh");
  }

  public void testParamExpansionNested() {
    doFileTest("sh");
  }
  
  public void testParamExpansion() {
    doFileTest("sh");
  }

  public void testArithmeticLiterals() {
    doFileTest("sh");
  }
  
  public void testReadCommand() {
    doFileTest("sh");
  }
  
  public void testUmlaut() {
    doFileTest("sh");
  }

  public void testSubshellExpr() {
    doFileTest("sh");
  }

  public void testIssue201() {
    doFileTest("sh");
  }

  public void testHeredoc() {
    doFileTest("sh");
  }

  public void testMultilineHeredoc() {
    doFileTest("sh");
  }

  public void testIssue118() {
    doFileTest("sh");
  }

  public void testIssue125() {
    doFileTest("sh");
  }

  public void testIssue199() {
    doFileTest("sh");
  }

  public void testIssue242() {
    doFileTest("sh");
  }

  public void testIssue246() {
    doFileTest("sh");
  }

  public void testIssue266() {
    doFileTest("sh");
  }

  public void testIssue270() {
    doFileTest("sh");
  }

  public void testIssue272() {
    doFileTest("sh");
  }
  
  public void testIssue300() {
    doFileTest("sh");
  }

  public void testIssue303() {
    doFileTest("sh");
  }

  public void testIssue308() {
    doFileTest("sh");
  }

  public void testIssue89() {
    doFileTest("sh");
  }

  public void testIssue320() {
    doFileTest("sh");
  }
  
  public void testIssue325() {
    doFileTest("sh");
  }

  public void testIssue327() {
    doFileTest("sh");
  }
  
  public void testIssue330() {
    doFileTest("sh");
  }
  
  public void testIssue330Var() {
    doFileTest("sh");
  }
  
  public void testIssue341() {
    doFileTest("sh");
  }
  
  public void testIssue343() {
    doFileTest("sh");
  }

  public void testIssue354() {
    doFileTest("sh");
  }
  
  public void testIssue389() {
    doFileTest("sh");
  }

  public void testTrapLexing() {
    doFileTest("sh");
  }
  
  public void testEvalLexing() {
    doFileTest("sh");
  }

  public void testIssue376() {
    doFileTest("sh");
  }

  public void testIssue367() {
    doFileTest("sh");
  }
  
  public void testIssue418() {
    doFileTest("sh");
  }

  public void testHereString() {
    doFileTest("sh");
  }

  public void testUnicode() {
    doFileTest("sh");
  }

  public void testLineContinuation() {
    doFileTest("sh");
  }
  
  public void testIssue358() {
    doFileTest("sh");
  }

  public void testIssue426() {
    doFileTest("sh");
  }

  public void testIssue431() {
    doFileTest("sh");
  }

  public void testIssue419() {
    doFileTest("sh");
  }

  public void testIssue401() {
    doFileTest("sh");
  }

  public void testIssue457() {
    doFileTest("sh");
  }

  public void testIssue458() {
    doFileTest("sh");
  }
  
  public void testIssue469() {
    doFileTest("sh");
  }
  
  public void testIssue474() {
    doFileTest("sh");
  }

  public void testIssue505() {
    doFileTest("sh");
  }
}
