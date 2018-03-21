// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyTypeCheckerInspectionTest extends PyInspectionTestCase {
  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeCheckerInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }

  public void testSimple() {
    doTest();
  }

  public void testStrUnicode() {
    doTest();
  }

  public void testListTuple() {
    doTest();
  }

  public void testBuiltinNumeric() {
    doTest();
  }

  public void testGenerator() {
    doTest();
  }

  // PY-4025
  public void testFunctionAssignments() {
    doTest();
  }

  public void testOldStyleClasses() {
    doTest();
  }

  public void testPartlyUnknownType() {
    doTest();
  }

  public void testTypeAssertions() {
    doTest();
  }

  public void testLocalTypeResolve() {
    doTest();
  }

  public void testSubscript() {
    doTest();
  }

  public void testComparisonOperators() {
    doTest();
  }

  public void testRightOperators() {
    doTest();
  }

  public void testStringInteger() {
    doTest();
  }

  public void testIsInstanceImplicitSelfTypes() {
    doTest();
  }

  public void testNotNone() {
    doTest();
  }

  public void testUnionReturnTypes() {
    doTest();
  }

  public void testEnumerateIterator() {
    doTest();
  }

  public void testGenericUserFunctions() {
    doTest();
  }

  public void testGenericUserClasses() {
    doTest();
  }

  public void testDictGenerics() {
    doTest();
  }

  // PY-5474
  public void testBadSubscriptExpression() {
    doTest();
  }

  // PY-5873
  public void testTypeOfRaiseException() {
    doTest();
  }

  // PY-6542
  public void testDictLiterals() {
    doTest();
  }

  // PY-6570
  public void testDictLiteralIndexing() {
    doTest();
  }

  // PY-6606
  public void testBuiltinBaseClass() {
    doTest();
  }

  // PY-18096
  public void testNamedTupleBaseClass() {
    doTest();
  }

  // PY-6803
  public void testPropertyAndFactoryFunction() {
    doTest();
  }

  // PY-7179
  public void testDecoratedFunction() {
    doTest();
  }

  // PY-6925
  public void testAssignedOperator() {
    doTest();
  }

  // PY-7244
  public void testGenericArguments() {
    doTest();
  }

  // PY-7757
  public void testOpenRead2K() {
    doTest();
  }

  // PY-8182
  public void testUnionWithSameMethods() {
    doTest();
  }

  // PY-8181
  public void testBytesSubclassAsStr() {
    doTest();
  }

  // PY-9118
  public void testNegativeIsInstance() {
    doTest();
  }

  // PY-7340
  public void testFieldWithNoneInStub() {
    doMultiFileTest();
  }

  public void testBoundedGeneric() {
    doTest();
  }

  public void testNotImportedClassInDocString() {
    doMultiFileTest();
  }

  // PY-6728
  public void testForLoopIteration() {
    doTest();
  }

  // PY-4285
  public void testMapReturnElementType() {
    doTest();
  }

  // PY-10413
  public void testFunctionParameterReturnType() {
    doTest();
  }

  // PY-10095
  public void testStringStartsWith() {
    doTest();
  }

  // PY-10854
  public void testSecondFormIter() {
    doTest();
  }

  public void testMetaClassIteration() {
    doTest();
  }

  // PY-10967
  public void testDefaultTupleParameter() {
    doTest();
  }

  // PY-14222
  public void testRecursiveDictAttribute() {
    doTest();
  }

  // PY-13394
  public void testContainsArguments() {
    doTest();
  }

  public void testExpectedStructuralType() {
    doTest();
  }

  public void testActualStructuralType() {
    doTest();
  }

  public void testStructuralTypesForNestedCalls() {
    doTest();
  }

  public void testIterateOverParamWithNoAttributes() {
    doTest();
  }

  public void testGetAttributeAgainstStructuralType() {
    doTest();
  }

  public void testComparisonOperatorsForNumericTypes() {
    doTest();
  }

  public void testClassNew() {
    doTest();
  }

  // PY-18275
  public void testStrFormat() {
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
  public void testUnicodeGetItemWithSlice() {
    doTest();
  }

  // PY-19884
  public void testAbsSetAndMutableSet() {
    doTest();
  }

  // PY-19884
  public void testSetMethods() {
    doTest();
  }

  // PY-11943
  public void testMutableMapping() {
    doTest();
  }

  // PY-16055
  public void testFunctionReturnType() {
    doTest();
  }

  // PY-19522
  public void testCsvRegisterDialect() {
    doMultiFileTest();
  }

  // PY-20364
  public void testActualBasestringExpectedUnionStrUnicode() {
    doTest();
  }

  // PY-21083
  public void testFloatFromhex() {
    doTest();
  }

  // PY-20073
  public void testMapArgumentsInOppositeOrderPy2() {
    doTest();
  }

  public void testPositionalArguments() {
    doTest();
  }

  // PY-19723
  public void testKeywordArguments() {
    doTest();
  }

  // PY-21350
  public void testBuiltinInputPy2() {
    doTest();
  }

  // PY-21350
  public void testBuiltinRawInput() {
    doTest();
  }

  // PY-22222
  public void testPassClassWithDunderSlotsToMethodThatUsesSlottedAttribute() {
    doTest();
  }

  // PY-22391
  public void testIteratingOverListAfterIfNot() {
    doTest();
  }

  // EA-98555, EA-98663
  public void testNullArgumentMappedToPositionalParameter() {
    doTest();
  }

  // PY-23138
  public void testHomogeneousTuplePlusHeterogeneousTupleWithTheSameElementsType() {
    doTest();
  }

  // PY-22763
  public void testChainedComparisons() {
    doTest();
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedModule() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doMultiFileTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doMultiFileTest);
  }

  // PY-23429
  public void testMatchingModuleAgainstStructuralType() {
    doMultiFileTest();
  }

  // PY-24287
  public void testPromotingBytearrayToStrAndUnicode() {
    doTest();
  }

  // PY-24930
  public void testCallOperator() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-24763
  public void testAnnotatedDunderInitInGenericClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testDunderInitAnnotatedAsNonNone() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-23367
  public void testComparingFloatAndInt() {
    doTest();
  }

  // PY-25120
  public void testIterateOverDictValueWhenItsTypeIsUnion() {
    doTest();
  }

  // PY-9662
  public void testBinaryExpressionWithUnknownOperand() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-16066
  public void testBasestringMatchesType() {
    doTest();
  }

  // PY-23864
  public void testClassObjectAndMetaclassCompatibility() {
    doTest();
  }

  // PY-21408
  public void testCallableAgainstStructural() {
    doTest();
  }

  public void testMatchingOpenFunctionCallTypesPy2() {
    doMultiFileTest();
  }

  // PY-21408
  public void testClassMetaAttrsAgainstStructural() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  public void testCallableInstanceAgainstCallable() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-26163
  public void testTypingNTAgainstStructural() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-26163
  public void testDefinitionAgainstStructural() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-28017
  public void testModuleWithGetAttr() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, this::doMultiFileTest);
  }

  // PY-26628
  public void testAgainstTypingProtocol() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-26628
  public void testAgainstTypingProtocolWithImplementedMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-26628
  public void testAgainstTypingProtocolWithImplementedVariable() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-26628
  public void testAgainstMergedTypingProtocols() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-26628
  public void testAgainstGenericTypingProtocol() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-26628
  public void testAgainstRecursiveTypingProtocol() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-26628
  public void testAgainstTypingProtocolWrongTypes() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-26628
  public void testTypingProtocolAgainstProtocol() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-26628
  public void testAgainstTypingProtocolDefinition() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-26628
  public void testTypingProtocolsInheritorAgainstHashable() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-28720
  public void testOverriddenBuiltinMethodAgainstTypingProtocol() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () ->
        doTestByText("import typing\n" +
                     "class Proto(typing.Protocol):\n" +
                     "    def function(self) -> None:\n" +
                     "        pass\n" +
                     "class Cls:\n" +
                     "    def __eq__(self, other) -> 'Cls':\n" +
                     "        pass\n" +
                     "    def function(self) -> None:\n" +
                     "        pass\n" +
                     "def method(p: Proto):\n" +
                     "    pass\n" +
                     "method(Cls())")
    );
  }

  // PY-28720
  public void testAgainstInvalidProtocol() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () ->
        doTestByText(
          "from typing import Any, Protocol\n" +
          "class B:\n" +
          "    def foo(self):\n" +
          "        ...\n" +
          "class C(B, Protocol):\n" +
          "    def bar(self):\n" +
          "        ...\n" +
          "class Bar:\n" +
          "    def bar(self):\n" +
          "        ...\n" +
          "def f(x: C) -> Any:\n" +
          "    ...\n" +
          "f(Bar())"
        )
    );
  }

  // PY-23161
  public void testGenericWithTypeVarBounds() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-27788
  public void testOverloadedFunctionAssignedToTargetInStub() {
    doMultiFileTest();
  }

  // PY-27949
  public void testAssigningToDictEntry() {
    doTest();
  }
}
