// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.LanguageLevel

class PyInvertIfConditionIntentionTest : PyIntentionTestCase() {

  fun testBrokenConditionAssignment() {
    doNegativeTest()
  }

  fun testBrokenConditionMultiline() {
    doNegativeTest()
  }

  fun testBrokenNoCondition() {
    doTest()
  }

  fun testBrokenNoConditionConditional() {
    doNegativeTest()
  }

  fun testBrokenNoConditionElse() {
    doTest()
  }

  fun testBrokenReturns() {
    doTest()
  }

  fun testBrokenReturnsMultiple() {
    doTest()
  }

  fun testBrokenStatementsElse() {
    doTest()
  }

  fun testBrokenStatementsIf() {
    doTest()
  }

  fun testCaretElse() {
    doTest()
  }

  fun testCaretElseStatements() {
    doNegativeTest()
  }

  fun testCaretEof() {
    doNegativeTest()
  }

  fun testCaretIf() {
    doTest()
  }

  fun testCaretIfStatements() {
    doNegativeTest()
  }

  fun testCommentsBoth() {
    doTest()
  }

  fun testCommentsDetached() {
    doTest()
  }

  fun testCommentsElse() {
    doTest()
  }

  fun testCommentsIf() {
    doTest()
  }

  fun testCommentsInlineBoth() {
    doTest()
  }

  fun testCommentsInlineElse() {
    doTest()
  }

  fun testCommentsInlineIf() {
    doTest()
  }

  fun testCommentsInlineNoElse() {
    doTest()
  }

  fun testCommentsInternal() {
    doTest()
  }

  fun testCommentsInternalNoElseReturn() {
    doTest()
  }

  fun testCommentsMultilineBoth() {
    doTest()
  }

  fun testCommentsMultilineElse() {
    doTest()
  }

  fun testCommentsMultilineIf() {
    doTest()
  }

  fun testCommentsMultilineNoElseBoth() {
    doTest()
  }

  fun testCommentsMultilineNoElseFollowup() {
    doTest()
  }

  fun testCommentsMultilineNoElseIf() {
    doTest()
  }

  fun testCommentsMultilineNoElseReturn() {
    doTest()
  }

  fun testCommentsMultilineNoElseReturnBoth() {
    doTest()
  }

  fun testCommentsMultilineNoElseReturnFollowup() {
    doTest()
  }

  fun testCommentsMultilineNoElseReturnIf() {
    doTest()
  }

  fun testCommentsMultilineNoElseReturnIfNoFollowup() {
    doTest()
  }


  fun testCommentsNoElseBoth() {
    doTest()
  }

  fun testCommentsNoElseFollowup() {
    doTest()
  }

  fun testCommentsNoElseIf() {
    doTest()
  }

  fun testCommentsNoElseReturn() {
    doTest()
  }

  fun testCommentsNoElseReturnBoth() {
    doTest()
  }

  fun testCommentsNoElseReturnFollowup() {
    doTest()
  }

  fun testCommentsNoElseReturnIf() {
    doTest()
  }

  fun testCommentsNoElseReturnIfNoFollowup() {
    doTest()
  }

  fun testCommentsNoinspectionBoth() {
    doTest()
  }

  fun testCommentsNoinspectionElse() {
    doTest()
  }

  fun testCommentsNoinspectionIf() {
    doTest()
  }

  fun testCommentsNoinspectionNoElseFollowup() {
    doTest()
  }

  fun testCommentsNoinspectionNoElseIf() {
    doTest()
  }

  fun testCommentsNoinspectionNoElseReturn() {
    doTest()
  }

  fun testCommentsNoinspectionNoElseReturnBoth() {
    doTest()
  }

  fun testCommentsNoinspectionNoElseReturnFollowup() {
    doTest()
  }

  fun testCommentsNoinspectionNoElseReturnIf() {
    doTest()
  }

  fun testCommentsNoinspectionNoElseReturnIfNoFollowup() {
    doTest()
  }

  fun testCommentsPylintBoth() {
    doTest()
  }

  fun testCommentsPylintElse() {
    doTest()
  }

  fun testCommentsPylintIf() {
    doTest()
  }

  fun testCommentsPylintInlineBoth() {
    doTest()
  }

  fun testCommentsPylintInlineElse() {
    doTest()
  }

  fun testCommentsPylintInlineIf() {
    doTest()
  }

  fun testCommentsPylintNoElseBoth() {
    doTest()
  }

  fun testCommentsPylintNoElseFollowup() {
    doTest()
  }

  fun testCommentsPylintNoElseIf() {
    doTest()
  }

  fun testCommentsPylintNoElseReturn() {
    doTest()
  }

  fun testCommentsPylintNoElseReturnBoth() {
    doTest()
  }

  fun testCommentsPylintNoElseReturnFollowup() {
    doTest()
  }

  fun testCommentsPylintNoElseReturnIf() {
    doTest()
  }

  fun testCommentsPylintNoElseReturnIfNoFollowup() {
    doTest()
  }

  fun testCommentsTypeBoth() {
    doTest()
  }

  fun testCommentsTypeBothMixedElse() {
    doTest()
  }

  fun testCommentsTypeBothMixedIf() {
    doTest()
  }

  fun testCommentsTypeElse() {
    doTest()
  }

  fun testCommentsTypeFollowup() {
    doTest()
  }

  fun testCommentsTypeFollowupIf() {
    doTest()
  }

  fun testCommentsTypeIf() {
    doTest()
  }

  fun testConditionCoerced() {
    doTest()
  }

  fun testConditionCoercedNot() {
    doTest()
  }

  fun testConditionEq() {
    doTest()
  }

  fun testConditionGt() {
    doTest()
  }

  fun testConditionLt() {
    doTest()
  }

  fun testConditionMapping() {
    doTest()
  }

  fun testConditionMultilineContinuation() {
    doTest()
  }

  fun testConditionMultilineExistingParens() {
    doTest()
  }

  fun testConditionMultilineParens() {
    doTest()
  }

  fun testConditionMultilineParensMultiple() {
    doTest()
  }

  fun testConditionMultilineParensNegative() {
    doTest()
  }

  fun testConditionMultiple() {
    doTest()
  }

  fun testConditionMultipleNested() {
    doTest()
  }

  fun testConditionMultipleTopParens() {
    doTest()
  }

  fun testForContinueNoElseFollowup() {
    doTest()
  }

  fun testConditional() {
    doTest()
  }

  fun testConditionAssignment() {
    doTest()
  }

  fun testConditionAssignmentCondition() {
    doTest()
  }

  fun testConditionAssignmentMultiple() {
    doTest()
  }

  fun testConditionAssignmentNegative() {
    doTest()
  }

  fun testConditionAsync() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, ::doTest)
  }

  fun testConditionChainedComparisons() {
    doTest()
  }

  fun testGeneralChained() {
    doNegativeTest()
  }

  fun testGeneralChainedElse() {
    doNegativeTest()
  }

  fun testGeneralElse() {
    doTest()
  }

  fun testGeneralNoElse() {
    doTest()
  }

  fun testGeneralNoElseFollowup() {
    doTest()
  }

  fun testGeneralNoElseFollowupReturn() {
    doTest()
  }

  fun testGeneralNoElseFollowupReturnOnly() {
    doTest()
  }

  fun testGeneralNoElseNestedReturn() {
    doTest()
  }

  fun testGeneralNoElseNestedReturns() {
    doTest()
  }

  fun testGeneralNoElseReturn() {
    doTest()
  }

  fun testGeneralNoElseReturnFollowup() {
    doTest()
  }

  fun testGeneralNoElseReturnFollowupValue() {
    doTest()
  }

  fun testGeneralNoElseTry() {
    doTest()
  }

  fun testModule() {
    doTest()
  }

  fun testModuleNoElse() {
    doTest()
  }

  fun testRaiseNoElse() {
    doTest()
  }

  fun testRaiseNoElseFollowup() {
    doTest()
  }

  fun testWhileBreakNoElse() {
    doTest()
  }

  fun testWhileBreakNoElseFollowup() {
    doTest()
  }

  fun testWhileContinueNoElse() {
    doTest()
  }

  fun testWhileContinueNoElseFollowup() {
    doTest()
  }

  fun testWhileContinueNoElseIf() {
    doTest()
  }

  fun testWhileReturnIf() {
    doTest()
  }

  fun testWhileReturnIfValue() {
    doTest()
  }

  fun testWhileReturnNoElseFollowup() {
    doTest()
  }

  fun testWhileReturnValueNoElseFollowup() {
    doTest()
  }

  // PY-63319
  fun testNonChainableBinaryExpressionAsComparisonLeftOperand() {
    doTest()
  }

  private fun doTest() {
    doIntentionTest(PyPsiBundle.message("INTN.invert.if.condition"))
  }

  private fun doNegativeTest() {
    doNegativeTest(PyPsiBundle.message("INTN.invert.if.condition"))
  }
}