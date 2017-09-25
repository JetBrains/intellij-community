/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyStringFormatInspectionTest extends PyInspectionTestCase {
  public void testBasic() {
    doTest();
  }

  // PY-2836
  public void testDictionaryArgument() {
    doTest();
  }

  // PY-4647
  public void testTupleMultiplication() {
    doTest();
  }

  // PY-6756
  public void testSlice() {
    doTest();
  }
  
  // PY-18954
  public void testOneElementDict() {
    doTest();
  }
  
  // PY-18725
  public void testDictWithReferenceKeys() {
    doTest();
  }
  
  public void testTooFewArgumentsNewStyleFormat() {
    doTest();
  }
  
  public void testTooManyArgumentsNewStyleFormat() {
    doTest();
  }
  
  public void testUnusedMappingNewStyleFormat() {
    doTest();
  }
  
  public void testIncompatibleTypesNewStyleFormat() {
    doTest();
  }
  
  public void testNewStyleMappingKeyWithSubscriptionListArg() {
    doTest();
  }
  
  public void testNewStyleMappingKeyWithSubscriptionRefArgs() {
    doTest();
  }
  
  public void testNewStyleMappingKeyWithSubscriptionDictArg() {
    doTest();
  }

  public void testNewStyleMappingKeyWithSubscriptionRefDictArg() {
    doTest();
  }
  
  public void testNewStyleMappingKeyWithSubscriptionParenArg() {
    doTest();
  }

  public void testNewStyleMappingKeyWithSubscriptionDictCall() {
    doTest();
  }
  
  public void testNewStyleStringWithPercentSymbol() {
    doTest();
  }
  
  public void testNewStylePackedAndNonPackedArgs() {
    doTest();
  }
  
  public void testNewStyleEmptyDictArg() {
    doTest();
  }
  
  public void testNewStyleDictLiteralExprInsideDictCall() {
    doTest();
  }
  
  public void testNewStylePositionalSubstitutionWithDictArg() {
    doTest();
  }
  
  public void testNewStylePackedReference() {
    doTest();
  }
  
  public void testNewStylePackedFunctionCall() {
    doTest();
  }
  
  public void testNewStyleStringRegularExpression() {
    doTest();
  }
  
  public void testNewStyleStringMapArg() {
    doTest();
  }
  
  public void testNewStyleDictLiteralWithReferenceKeys() {
    doTest();
  }
  
  public void testNewStyleDictLiteralWithNumericKeys() {
    doTest();
  }
  
  public void testNewStyleCallExpressionArgument() {
    doTest();
  }
  
  public void testPercentStringWithFormatStringReplacementSymbols() {
    doTest();
  }
  
  public void testPercentStringPositionalWithEmptyDictArg() {
    doTest();
  }
  
  public void testPercentStringWithDictElement() {
    doTest();
  }
  
  public void testPercentStringWithDictCall() {
    doTest();
  }
  
  public void testPercentStringWithDictArgument() {
    doTest();
  }
  
  public void testPercentStringPositionalListArgument() {
    doTest();
  }
  
  public void testPercentStringPositionalDictArgument() {
    doTest();
  }
  
  public void testPercentStringKeywordSetArgument() {
    doTest();
  }
  
  public void testPercentStringKeywordListArgument() {
    doTest();
  }
  
  public void testPercentStringCallUnionArgument() {
    doTest();
  } 
  
  public void testPercentStringCallArgument() {
    doTest();
  }

  public void testMultilineString() {
    doTest();
  }
  
  // PY-8325
  public void testTooFewMappingKeys() {
    doTest();
  }

  public void testEscapedString() {
    doTest();
  }

  //PY-21166
  public void testUnsupportedFormatSpecifierNewStyleFormatting() {
    doTest();
  }
  
  // PY-21156
  public void testPackedStringTooFewArguments() {
    doTest();
  }
  
  // PY-21156
  public void testPackedDictCallUnusedMappingKey() {
    doTest();
  }

  public void testUnionCallType() {
    doTest();
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyStringFormatInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
