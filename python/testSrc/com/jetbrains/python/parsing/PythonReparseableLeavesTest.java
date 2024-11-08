// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.jetbrains.python.PythonTestUtil;

public class PythonReparseableLeavesTest extends PythonIncrementalParsingTestCase {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/psi/incrementalParsing/reparseableLeaves";
  }

  public void testIdentifierInClassName() {
    doTest();
  }

  public void testIdentifierInMethodName() {
    doTest();
  }

  public void testIdentifierKeywordInMethodName() {
    doTest(true, false);
  }

  public void testEmptyIdentifier() {
    doTest(true, false);
  }

  public void testIdentifierInAssignment() {
    doTest();
  }

  public void testIdentifierInParameterList() {
    doTest();
  }

  public void testIdentifierInParameterListChangedToKwarg() {
    doTest();
  }

  public void testIdentifierInParameterListChangedToStarArg() {
    doTest();
  }

  public void testIdentifierWhitespaceAfter() {
    doTest();
  }

  public void testIdentifierWhitespaceBefore() {
    doTest();
  }

  public void testIdentifierWhitespaceInTheMiddle() {
    doTest(true, false);
  }

  public void testSingleQuotationMarksStringSimpleChange() {
    doTest();
  }

  public void testDoubleQuotationMarksStringSimpleChange() {
    doTest();
  }

  public void testDoubleQuotationMarksTripleQuotedStringSimpleChange() {
    doTest();
  }

  public void testStringChangedToReference() {
    doTest();
  }

  public void testSingleQuotedStringChangedToTripleQuoted() {
    doTest();
  }

  public void testSingleQuotedStringChangedToTripleQuotedWithDoubleQuotationMarks() {
    doTest();
  }

  public void testTripleQuotedStringChangedToSingleQuoted() {
    doTest();
  }

  public void testSimpleEndOfLineComment() {
    doTest();
  }

  public void testSingleLineCommentReplacedWithMultilineStringLiteral() {
    doTest();
  }

  public void testSingleQuotedStringChangedToMultipleTokens() {
    doTest();
  }

  public void testTripleQuotedStringChangedToMultipleTokens() {
    doTest();
  }

  public void testSimpleDocstring() {
    doTest();
  }

  public void testEndOfLineCommentWithNewExpressionOnTheNextLine() {
    doTest();
  }

  public void testMultilineTripleQuotedStringChangedToSingleLineSingleQuoted() {
    doTest();
  }

  public void testFStringNotReparsed() {
    doTest();
  }
  
  public void testSingleQuotedStringReparsedInsideFString() {
    doTest();
  }
  
  public void testIdentifierReparsedInsideFString() {
    doTest();
  }
  
  public void testIdentifierBeforeFloatingPointFormatReparsedInFSting() {
    doTest();
  }

  public void testTripleQuotedStringReparsedInsideFString() {
    doTest();
  }

  public void testMultilineTripleQuotedStringReparsedInsideFString() {
    doTest();
  }

  public void testNewQuoteBeforeSingleQuotedString() {
    doTest(true, false);
  }

  public void testNewQuoteBeforeDoubleQuotedString() {
    doTest(true, false);
  }

  public void testNewQuoteBeforeSingleQuotedStringRecovery() {
    doTest(false, true);
  }

  public void testNewQuoteBeforeDoubleQuotedStringRecovery() {
    doTest(false, true);
  }

  public void testTripleQuotedStringChangedToThreeQuotesSingleQuotationMarks() {
    doTest(true, false);
  }

  public void testStringWithTwoQuotationMarksInTheBeginningChangedToTripleQuoted() {
    doTest(false, true);
  }
}