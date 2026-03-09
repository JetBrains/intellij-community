// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyStringFormatInspectionTest : PyInspectionTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor? = ourPy2Descriptor

  fun testBasic() = doTest()

  // PY-2836
  fun testDictionaryArgument() = doTest()

  // PY-4647
  fun testTupleMultiplication() = doTest()

  // PY-6756
  fun testSlice() = doTest()

  // PY-18954
  fun testOneElementDict() = doTest()

  // PY-18725
  fun testDictWithReferenceKeys() = doTest()

  fun testTooFewArgumentsNewStyleFormat() = doTest()

  fun testTooManyArgumentsNewStyleFormat() = doTest()

  fun testUnusedMappingNewStyleFormat() = doTest()

  fun testIncompatibleTypesNewStyleFormat() = doTest()

  fun testNewStyleMappingKeyWithSubscriptionListArg() = doTest()

  fun testNewStyleMappingKeyWithSubscriptionRefArgs() = doTest()

  fun testNewStyleMappingKeyWithSubscriptionDictArg() = doTest()

  fun testNewStyleMappingKeyWithSubscriptionRefDictArg() = doTest()

  fun testNewStyleMappingKeyWithSubscriptionParenArg() = doTest()

  fun testNewStyleMappingKeyWithSubscriptionDictCall() = doTest()

  fun testNewStyleStringWithPercentSymbol() = doTest()

  fun testNewStylePackedAndNonPackedArgs() = doTest()

  fun testNewStyleEmptyDictArg() = doTest()

  fun testNewStyleDictLiteralExprInsideDictCall() = doTest()

  fun testNewStylePositionalSubstitutionWithDictArg() = doTest()

  fun testNewStylePackedReference() = doTest()

  fun testNewStylePackedFunctionCall() = doTest()

  fun testNewStyleStringRegularExpression() = doTest()

  fun testNewStyleStringMapArg() = doTest()

  fun testNewStyleDictLiteralWithReferenceKeys() = doTest()

  fun testNewStyleDictLiteralWithNumericKeys() = doTest()

  fun testNewStyleCallExpressionArgument() = doTest()

  // PY-27601
  fun testNewStylePositionalSubstitutionAfterKeywordSubstitution() = doTest()

  fun testNewStyleAutomaticAfterManualNumbering() = doTest()

  fun testNewStyleManualAfterAutomaticNumbering() = doTest()

  fun testPercentStringWithFormatStringReplacementSymbols() = doTest()

  fun testPercentStringPositionalWithEmptyDictArg() = doTest()

  fun testPercentStringWithDictElement() = doTest()

  fun testPercentStringWithDictCall() = doTest()

  fun testPercentStringWithDictArgument() = doTest()

  fun testPercentStringPositionalListArgument() = doTest()

  fun testPercentStringPositionalDictArgument() = doTest()

  fun testPercentStringKeywordSetArgument() = doTest()

  fun testPercentStringKeywordListArgument() = doTest()

  fun testPercentStringCallUnionArgument() = doTest()

  fun testPercentStringCallArgument() = doTest()

  fun testMultilineString() = doTest()

  // PY-8325
  fun testTooFewMappingKeys() = doTest()

  fun testEscapedString() = doTest()

  //PY-21166
  fun testUnsupportedFormatSpecifierNewStyleFormatting() = doTest()

  // PY-21156
  fun testPackedStringTooFewArguments() = doTest()

  // PY-21156
  fun testPackedDictCallUnusedMappingKey() = doTest()

  fun testUnionCallType() = doTest()

  // PY-26028
  fun testSOEOnReassignedFormatArgument() = doTest()

  // PY-33218
  fun testNoTypeMismatchOnElementsOfTupleDeclaration() = doTest()

  override fun getInspectionClass() = PyStringFormatInspection::class.java

  override fun isLowerCaseTestFile() = false
}
