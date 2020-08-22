// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.getLatest());
  }

  @Override
  protected void tearDown() throws Exception {
    setLanguageLevel(null);
    super.tearDown();
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
    doTest();
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
  public void testMapArgumentsInOppositeOrderPy3() {
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

  // PY-21302
  public void testInitializingNewType() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-21302
  public void testNewTypeAsParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-21302
  public void testNewTypeInheritance() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
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

  // PY-25994
  public void testUnresolvedReceiverGeneric() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  public void testMatchingOpenFunctionCallTypesPy3() {
    doMultiFileTest();
  }

  public void testChainedComparisonsGenericMatching() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-27398
  public void testInitializingDataclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, () -> super.doMultiFileTest());
  }

  // PY-28442
  public void testDataclassClsCallType() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, () -> super.doMultiFileTest());
  }

  // PY-26354
  public void testInitializingAttrs() {
    doTestByText("import attr\n" +
                 "import typing\n" +
                 "\n" +
                 "@attr.s\n" +
                 "class Weak1:\n" +
                 "    x = attr.ib()\n" +
                 "    y = attr.ib(default=0)\n" +
                 "    z = attr.ib(default=attr.Factory(list))\n" +
                 "    \n" +
                 "Weak1(1, \"str\", 2)\n" +
                 "\n" +
                 "\n" +
                 "@attr.s\n" +
                 "class Weak2:\n" +
                 "    x = attr.ib()\n" +
                 "    \n" +
                 "    @x.default\n" +
                 "    def __init_x__(self):\n" +
                 "        return 1\n" +
                 "    \n" +
                 "Weak2(\"str\")\n" +
                 "\n" +
                 "\n" +
                 "@attr.s\n" +
                 "class Strong:\n" +
                 "    x = attr.ib(type=int)\n" +
                 "    y = attr.ib(default=0, type=int)\n" +
                 "    z = attr.ib(default=attr.Factory(list), type=typing.List[int])\n" +
                 "    \n" +
                 "Strong(1, <warning descr=\"Expected type 'int', got 'str' instead\">\"str\"</warning>, <warning descr=\"Expected type 'list[int]', got 'list[str]' instead\">[\"str\"]</warning>)");
  }

  // PY-28957
  public void testDataclassesReplace() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, () -> super.doMultiFileTest());
  }

  // PY-28127 PY-31424
  public void testInitializingTypeVar() {
    doTest();
  }

  // PY-24832
  public void testAssignment() {
    doTest();
  }

  // PY-24832
  public void testReAssignment() {
    doTest();
  }

  // PY-24832
  public void testTypeCommentAssignment() {
    doTest();
  }

  // PY-24832
  public void testTypeDeclarationAndAssignment() {
    doTest();
  }

  // PY-24832
  public void testClassLevelAssignment() {
    doTest();
  }

  // PY-24832
  public void testNoTypeMismatchInAssignmentWithoutTypeAnnotation() {
    doTest();
  }

  // PY-35235
  public void testTypingLiteralStrings() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Literal\n" +
                         "\n" +
                         "a: Literal[\"abc\"] = undefined\n" +
                         "b: Literal[b\"abc\"] = undefined\n" +
                         "\n" +
                         "def foo1(p1: Literal[\"abc\"]):\n" +
                         "    pass\n" +
                         "foo1(a)\n" +
                         "foo1(<warning descr=\"Expected type 'Literal[\\\"abc\\\"]', got 'Literal[b\\\"abc\\\"]' instead\">b</warning>)\n" +
                         "\n" +
                         "def foo2(p1: Literal[b\"abc\"]):\n" +
                         "    pass\n" +
                         "foo2(<warning descr=\"Expected type 'Literal[b\\\"abc\\\"]', got 'Literal[\\\"abc\\\"]' instead\">a</warning>)\n" +
                         "foo2(b)\n" +
                         "\n" +
                         "def foo3(p1: str):\n" +
                         "    pass\n" +
                         "foo3(a)\n" +
                         "foo3(<warning descr=\"Expected type 'str', got 'Literal[b\\\"abc\\\"]' instead\">b</warning>)\n" +
                         "\n" +
                         "def foo4(p1: bytes):\n" +
                         "    pass\n" +
                         "foo4(<warning descr=\"Expected type 'bytes', got 'Literal[\\\"abc\\\"]' instead\">a</warning>)\n" +
                         "foo4(b)\n")
    );
  }

  // PY-42418
  public void testParametrizedBuiltinCollectionsAndTheirTypingAliasesAreEquivalent() {
    doTest();
  }

  // PY-42418
  public void testParametrizedBuiltinTypeAndTypingTypeAreEquivalent() {
    doTest();
  }

  // PY-30747
  public void testPathlibPathMatchingOsPathLike() {
    doTestByText(
      "import pathlib\n" +
      "import os\n" +
      "\n" +
      "def foo(p: pathlib.Path):\n" +
      "    with open(p) as file:\n" +
      "        pass\n" +
      "\n" +
      "p1: pathlib.Path\n" +
      "p2: os.PathLike[bytes] = p1  # false negative, see PyTypeChecker.matchGenerics\n" +
      "p3: os.PathLike[str] = p1"
    );
  }

  // PY-41847
  public void testTypingAnnotatedType() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTestByText("from typing import Annotated\n" +
                     "A = Annotated[bool, 'Some constraint']\n" +
                     "a: A = <warning descr=\"Expected type 'bool', got 'str' instead\">'str'</warning>\n" +
                     "b: A = True\n" +
                     "c: Annotated[bool, 'Some constraint'] = <warning descr=\"Expected type 'bool', got 'str' instead\">'str'</warning>\n" +
                     "d: Annotated[str, 'Some constraint'] = 'str'\n");
      }
    );
  }

  // PY-41847
  public void testTypingAnnotatedTypeMultiFile() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doMultiFileTest);
  }
}
