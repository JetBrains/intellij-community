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

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class Py3TypeCheckerInspectionTest extends PyInspectionTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyTypeCheckerInspection/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeCheckerInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }

  @Override
  protected String getTestCaseDirectory() {
    return TEST_DIRECTORY;
  }

  @Override
  protected void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> super.doTest());
  }

  @Override
  protected void doMultiFileTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> super.doMultiFileTest());
  }

  // PY-9289
  public void testWithOpenBinaryPy3() {
    doTest();
  }

  // PY-10660
  public void testStructUnpackPy3() {
    doMultiFileTest();
  }

  public void testBuiltinsPy3() {
    doTest();
  }

  // PY-16125
  public void testTypingIterableForLoop() {
    doTest();
  }

  // PY-16146
  public void testTypingListSubscriptionExpression() {
    doTest();
  }

  // PY-16855
  public void testTypingTypeVarWithUnresolvedBound() {
    doTest();
  }

  // PY-16898
  public void testAsyncForIterable() {
    doTest();
  }

  // PY-18275
  public void testStrFormatPy3() {
    doTest();
  }
  
  // PY-18762
  public void testHomogeneousTuples() {
    myFixture.copyDirectoryToProject("typing/typing.py", TEST_DIRECTORY);
    doTest();
  }

  // PY-9924
  public void testTupleGetItemWithSlice() {
    doTest();
  }

  // PY-9924
  public void testListGetItemWithSlice() {
    doTest();
  }

  // PY-20460
  public void testStringGetItemWithSlice() {
    doTest();
  }

  // PY-20460
  public void testBytesGetItemWithSlice() {
    doTest();
  }

  // PY-19796
  public void testOrd() {
    doTest();
  }

  // PY-12944
  public void testDelegatedGenerator() {
    doTest();
  }

  // PY-16055
  public void testFunctionReturnTypePy3() {
    doTest();
  }

  // PY-20770
  public void testAsyncForOverAsyncGenerator() {
    doTest();
  }

  // PY-20770
  public void testForOverAsyncGenerator() {
    doTest();
  }

  // PY-20770
  public void testAsyncComprehensionsOverAsyncGenerator() {
    doTest();
  }

  // PY-20770
  public void testAsyncComprehensionsOverGenerator() {
    doTest();
  }

  // PY-20770
  public void testComprehensionsOverAsyncGenerator() {
    doTest();
  }

  // PY-20769
  public void testPathLikePassedToStdlibFunctions() {
    doMultiFileTest();
  }

  // PY-21048
  public void testAsyncFunctionReturnType() {
    doTest();
  }

  // PY-20967
  public void testAsyncFunctionAnnotatedToReturnNone() {
    doTest();
  }

  // PY-20709
  public void testGeneratorReturnType() {
    doTest();
  }

  // PY-20657, PY-21916
  public void testGeneratorAnnotatedToReturnIterable() {
    doTest();
  }

  // PY-20657, PY-21916
  public void testAsyncGeneratorAnnotatedToReturnAsyncIterable() {
    doTest();
  }

  // PY-21083
  public void testFloatFromhex() {
    doTest();
  }

  // PY-20073
  public void testMapArgumentsInOppositeOrder() {
    doTest();
  }

  // PY-21350
  public void testBuiltinInputPy3() {
    doTest();
  }

  // PY-200057
  public void testClassObjectType() {
    doTest();
  }

  // PY-20057
  public void testTypeAndClassObjectTypesCompatibility() {
    doTest();
  }

  // PY-20057
  public void testClassObjectTypeWithUnion() {
    doTest();
  }

  // PY-22730
  public void testOptionalOfBoundTypeVarInWarnings() {
    doTest();
  }

  // PY-22769
  public void testReplaceCalledOnUnionOfStrAndBytesWithStrArguments() {
    doTest();
  }

  // PY-23053
  public void testUnboundTypeVarsMatchClassObjectTypes() {
    doTest();
  }

  // PY-22513
  public void testGenericKwargs() {
    doTest();
  }

  public void testTypingNamedTupleAsParameter() {
    doTest();
  }

  // PY-17962
  public void testTypingCallableCall() {
    doTest();
  }

  // PY-23057
  public void testEllipsisInFunctionWithSpecifiedReturnType() {
    doTest();
  }

  // PY-23239, PY-23253
  public void testInitializingTypingNamedTuple() {
    doTest();
  }

  // PY-24287
  public void testPromotingBytearrayToBytes() {
    doTest();
  }

  // PY-25045
  public void testUnionOfIntAndFloatShouldBeConsideredAsDividable() {
    doTest();
  }

  // PY-23289
  // PY-23391
  // PY-24194
  // PY-24789
  public void testTypingSupports() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }
}
