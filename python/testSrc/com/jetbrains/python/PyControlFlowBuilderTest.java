// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nullable;

public class PyControlFlowBuilderTest extends LightMarkedTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  @Override
  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/codeInsight/controlflow/";
  }

  private void doTest() {
    final String testName = getTestName(false);
    configureByFile(testName + ".py");
    final ControlFlow flow = ControlFlowCache.getControlFlow((PyFile)myFile);
    final String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
   }

  public void testAssert() {
    doTest();
  }

  public void testAssertFalse() {
    doTest();
  }

  public void testPass() {
    doTest();
  }

  public void testFile() {
    doTest();
  }

  public void testIf() {
    doTest();
  }

  public void testFor() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testBreak() {
    doTest();
  }

  public void testContinue() {
    doTest();
  }

  public void testReturn() {
    doTest();
  }

  public void testTry() {
    doTest();
  }

  public void testImport() {
    doTest();
  }

  public void testListComp() {
    doTest();
  }

  public void testAssignment() {
    doTest();
  }

  public void testAssignment2() {
    doTest();
  }

  public void testAugAssignment() {
    doTest();
  }

  public void testSliceAssignment() {
    doTest();
  }

  public void testIfElseReturn() {
    doTest();
  }

  public void testRaise() {
    doTest();
  }

  public void testReturnFor() {
    doTest();
  }

  public void testForIf() {
    doTest();
  }
  
  // PY-80824
  public void testIfFor() {
    doTest();
  }

  public void testForReturn() {
    doTest();
  }
  
  // PY-80564
  public void testReturnComprehensionFromExcept() {
    doTest();
  }

  public void testForTryContinue() {
    doTest();
  }

  public void testTryRaiseFinally() {
    doTest();
  }

  public void testTryExceptElseFinally() {
    doTest();
  }

  public void testTryFinally() {
    doTest();
  }

  public void testDoubleTry() {
    doTest();
  }

  public void testTryTry() {
    doTest();
  }

  public void testIsinstance() {
    doTest();
  }

  public void testLambda() {
    doTest();
  }

  public void testManyIfs() {
    doTest();
  }

  public void testSuperclass() {
    doTest();
  }

  public void testDefaultParameterValue() {
    doTest();
  }

  public void testLambdaDefaultParameter() {
    doTest();
  }

  public void testDecorator() {
    doTestFirstStatement();
  }

  public void testSetComprehension() {
    doTest();
  }

  public void testTypeAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  public void testQualifiedSelfReference() {
    final String testName = getTestName(false);
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    final ControlFlow flow = ControlFlowCache.getControlFlow(pyClass.getMethods()[0]);
    check(fullPath, flow);
  }

  public void testSelf() {
    final String testName = getTestName(false);
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    final ControlFlow flow = ControlFlowCache.getControlFlow(pyClass.getMethods()[0]);
    check(fullPath, flow);
  }

  public void testTryBreak() {
    final String testName = getTestName(false);
    configureByFile(testName + ".py");
    final ControlFlow flow = ControlFlowCache.getControlFlow((PyFunction)((PyFile)myFile).getStatements().get(0));
    final String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
  }

  public void testFunction() {
    doTestFirstStatement();
  }

  // PY-7784
  public void testAssertFalseArgument() {
    doTest();
  }

  public void testConditionalExpression() {
    doTest();
  }

  // PY-20744, PY-20864
  public void testVariableAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-21175
  public void testImplicitNegativeTypeAssertionAfterIf() {
    doTest();
  }

  // PY-21175
  public void testImplicitNegativeTypeAssertionAfterTwoNestedIf() {
    doTest();
  }

  // PY-20889
  public void testTypesInAndBooleanExpression() {
    doTest();
  }

  // PY-20889
  public void testTypesInOrBooleanExpression() {
    doTest();
  }

  // PY-25974
  public void testAndBooleanExpression() {
    doTest();
  }

  // PY-25974
  public void testOrBooleanExpression() {
    doTest();
  }

  // PY-14840
  // PY-22003
  public void testPositiveIteration() {
    doTest();
  }

  // PY-24750
  public void testIfFalse() {
    doTest();
  }

  // PY-24750
  public void testIfTrue() {
    doTest();
  }

  // PY-24750
  public void testIfElifTrue() {
    doTest();
  }

  // PY-24750
  public void testIfElifFalse() {
    doTest();
  }

  // PY-28972
  public void testWhileTrueElse() {
    doTest();
  }

  // PY-13919
  public void testWithRaiseException() {
    doTest();
  }

  // PY-37718
  public void testWithAssert() {
    doTest();
  }
  
  // PY-37718
  public void testWithAssertFalse() {
    doTest();
  }

  // PY-51564
  public void testWithSeveralContextsAssert() {
    doTest();
  }

  // PY-29767
  public void testContinueInPositiveIteration() {
    doTest();
  }

  // PY-33886
  public void testAssignmentExpression() {
    doTest();
  }

  // PY-4537
  public void testDelete() {
    doTest();
  }

  // PY-4537
  public void testDeleteSubscriptionAndSlice() {
    doTest();
  }

  // PY-39262
  public void testAssignmentExpressionInsideBinaryInWhile() {
    doTest();
  }

  // PY-39262
  public void testAssignmentExpressionInsideBinaryInWhileElse() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseCapturePattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseWildcardPattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseLiteralPattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseBindingSequencePattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementTwoClausesCapturePatternFirst() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementTwoClausesCapturePatternLast() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseAliasedRefutableOrPattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseRefutableOrPattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseIrrefutableOrPatternCaptureVariantFirst() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseIrrefutableOrPatternCaptureVariantLast() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseRefutableOrPatternWithNonBindingVariants() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseRefutableOrPatternWithWildcard() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseSequencePatternWithSingleOrPattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseNestedOrPatterns() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseClassPattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseMappingPattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseSequencePattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseParenthesizedCapturePattern() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseNamedSingleStarPatternIsIrrefutable() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseWildcardSingleStarPatternIsIrrefutable() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseDoubleStarPatternIsIrrefutable() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementClauseWithBreak() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementClauseWithContinue() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementClauseWithReturn() {
    doTestFirstStatement();
  }

  // PY-48760
  public void testMatchStatementNestedMatchStatementLastInClause() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementNestedMatchStatement() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseTrivialGuard() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseDisjunctionGuard() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseConjunctionGuard() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseDisjunctionConjunctionGuard() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseRefutablePatternAndConjunctionGuard() {
    doTest();
  }

  // PY-48760
  public void testMatchStatementSingleClauseGuardWithNonTopLevelDisjunction() {
    doTest();
  }

  // PY-7758
  public void testControlFlowIsAbruptAfterExit() {
    doTest();
  }

  // PY-7758
  public void testControlFlowIsAbruptAfterSysExit() {
    doTest();
  }

  public void testTypeGuard() {
    doTest();
  }

  public void testTypeGuardConjunct() {
    doTest();
  }

  public void testTypeGuardWhile() {
    doTest();
  }

  // PY-23859
  public void testControlFlowIsAbruptAfterSelfFail() {
    final String testName = getTestName(false);
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final PyClass pyClass = ((PyFile)myFile).getTopLevelClasses().get(0);
    final ControlFlow flow = ControlFlowCache.getControlFlow(pyClass.getMethods()[0]);
    check(fullPath, flow);
  }

  // PY-24273
  public void testControlFlowIsAbruptAfterNoReturn() {
    doTest();
  }

  // TODO migrate this test class to Python 3 SDK by default to make this test work
  // PY-53703
  //public void testControlFlowIsAbruptAfterNever() {
  //  doTest();
  //}

  // PY-61878
  public void testTypeAliasStatement() {
    doTest();
  }

  // PY-61878
  public void testTypeAliasStatementWithTypeParameterList() {
    doTestFirstStatement();
  }

  // PY-61877
  public void testTypeParameterListInFunctionDeclaration() {
    doTestFirstStatement();
  }

  // PY-61877
  public void testTypeParameterListInClassDeclaration() {
    doTestFirstStatement();
  }

  public void testFunctionAnnotationsAndParameterDefaultsAreExcludedFromItsGraph() {
    doTestFirstStatement();
  }

  public void testFunctionAnnotationsAndParameterDefaultsAreIncludedInEnclosingScopeGraph() {
    doTest();
  }

  // PY-61877 PY-82699
  public void testNewStyleGenericFunctionAnnotationsAreNotIncludedInItsGraph() {
    doTestFirstStatement();
  }

  // PY-61877
  public void testNewStyleGenericFunctionAnnotationsAreNotIncludedInEnclosingScopeGraph() {
    doTest();
  }

  // PY-79910
  public void testTryExceptNoFinally() {
    doTest();
  }

  // PY-80471
  public void testWhileInsideIfTrue() {
    doTest();
  }

  // PY-80733
  public void testWhileTrueBreakInsideExcept() {
    doTest();
  }

  private void doTestFirstStatement() {
    final String testName = getTestName(false);
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final ControlFlow flow = ControlFlowCache.getControlFlow((ScopeOwner)((PyFile)myFile).getStatements().get(0));
    check(fullPath, flow);
  }

  private static void check(final String fullPath, final ControlFlow flow) {
    final String actualCFG = StringUtil.join(flow.getInstructions(), Object::toString, "\n");
    UsefulTestCase.assertSameLinesWithFile(fullPath, actualCFG, true);
  }
}
