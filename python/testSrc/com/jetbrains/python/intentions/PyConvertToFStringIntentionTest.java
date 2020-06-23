/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author Mikhail Golubev
 */
public class PyConvertToFStringIntentionTest extends PyIntentionTestCase {
  
  private void doTest() {
    doTest(PyPsiBundle.message("INTN.convert.to.fstring.literal"), LanguageLevel.PYTHON36);
  }

  private void doNegativeTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doNegativeTest(PyPsiBundle.message("INTN.convert.to.fstring.literal")));
  }
  
  public void testFormatMethodByteString() {
    doNegativeTest();
  }

  public void testFormatMethodGluedString() {
    doNegativeTest();
  }

  public void testPercentOperatorDynamicWidth() {
    doNegativeTest();
  }

  public void testPercentOperatorDynamicPrecision() {
    doNegativeTest();
  }
  
  // PY-21243
  public void testPercentOperatorFallbackResolveResultForNamedChunk() {
    doNegativeTest();
  }

  // PY-21243
  public void testPercentOperatorFallbackResolveResultForPositionalChunk() {
    doNegativeTest();
  }

  public void testPercentOperatorSingleExpression() {
    doTest();
  }

  public void testPercentOperatorSimpleTuple() {
    doTest();
  }

  public void testPercentOperatorSimpleDictLiteral() {
    doTest();
  }

  public void testPercentOperatorSimpleDictConstructorCall() {
    doTest();
  }

  public void testPercentOperatorQuotesInsideInlinedExpressions() {
    doTest();
  }

  public void testPercentOperatorMultilineExpression() {
    doNegativeTest();
  }

  public void testPercentOperatorExpressionWithBackslash() {
    doNegativeTest();    
  }

  public void testPercentOperatorExpressionContainsOriginalHostQuote() {
    doNegativeTest();
  }

  public void testPercentOperatorExpressionContainsAlternativeHostQuote() {
    doNegativeTest();
  }

  // PY-24232
  public void testPercentOperatorRemovingEscapingFromPercentSigns() {
    doTest();
  }

  // PY-38319
  public void testPercentOperatorAddingEscapingToCurlyBraces() {
    doTest();
  }

  public void testFormatMethodSimple() {
    doTest();
  }

  public void testFormatMethodAttributeReferences() {
    doTest();
  }

  public void testFormatMethodItemAccess() {
    doTest();
  }
  
  // PY-21245
  public void testFormatMethodIndexContainsHostAlternativeQuote() {
    doTest();
  }
  
  // PY-21245
  public void testFormatMethodIndexContainsQuoteOfMultilineHost() {
    doTest();    
  }
  
  // PY-21245
  public void testFormatMethodIndexContainsAlternativeQuoteOfMultilineHost() {
    doTest();    
  }

  // PY-21245
  public void testFormatMethodIndexContainsBothTypesOfQuotesInsideMultilineHost() {
    doTest();
  }

  public void testFormatMethodIndexContainsBackslash() {
    doTest();
  }

  public void testPercentOperatorWidthAndPrecision() {
    doTest();
  }

  public void testFormatMethodWrongStringMethod() {
    doNegativeTest();
  }

  public void testPercentOperatorInlineMultilineTripleQuotedString() {
    doNegativeTest();
  }

  public void testPercentOperatorInlineOneLineTripleQuotedString() {
    doTest();
  }

  public void testPercentOperatorInlineSingleQuotedStringInsideTripleQuotedString() {
    doTest();
  }

  // PY-21244
  public void testFormatMethodNestedFields() {
    doTest();
  }
  
  // PY-21244
  public void testFormatMethodNestedFields2() {
    doTest();
  }

  // PY-21244
  public void testFormatMethodNestedFields3() {
    doTest();
  }

  // PY-21244
  public void testFormatMethodParentFieldUnresolved() {
    doNegativeTest();
  }

  // PY-21246
  public void testFormatMethodWrapExpressionsInParentheses() {
    doTest();
  }

  // PY-21246
  public void testPercentOperatorWrapLambdaInParentheses() {
    doTest();
  }
}
