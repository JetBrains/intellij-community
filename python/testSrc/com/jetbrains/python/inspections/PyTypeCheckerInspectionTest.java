// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyTypeCheckerInspectionTest extends PyInspectionTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
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

  // PY-22222, PY-29233
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

  // PY-11977
  public void testMetaclassInstanceMembersProvidedAndNoTypeCheckWarningWhenPassIntoMethodUseThisMembers() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
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

  // PY-43133
  public void testHierarchyAgainstProtocol() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText(
        "from typing import Protocol\n" +
        "\n" +
        "class A:\n" +
        "    def f1(self, x: str):\n" +
        "        pass\n" +
        "\n" +
        "class B(A):\n" +
        "    def f2(self, y: str):\n" +
        "        pass\n" +
        "\n" +
        "class P(Protocol):\n" +
        "    def f1(self, x: str): ...\n" +
        "    def f2(self, y: str): ...\n" +
        "\n" +
        "def test(p: P):\n" +
        "    pass\n" +
        "\n" +
        "b = B()\n" +
        "test(b)"
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

  // PY-27231
  public void testStructuralAndNone() {
    doTestByText("def func11(value):\n" +
                 "    if value is not None and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "def func12(value):\n" +
                 "    if None is not value and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "def func21(value):\n" +
                 "    if value is None and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "def func22(value):\n" +
                 "    if None is value and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "func11(None)\n" +
                 "func12(None)\n" +
                 "func21(None)\n" +
                 "func22(None)\n" +
                 "\n" +
                 "\n" +
                 "def func31(value):\n" +
                 "    if value and None and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "def func32(value):\n" +
                 "    if value is value and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "def func33(value):\n" +
                 "    if None is None and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "def func34(value):\n" +
                 "    a = 2\n" +
                 "    if a is a and value != 1:\n" +
                 "        pass\n" +
                 "\n" +
                 "\n" +
                 "func31(<warning descr=\"Expected type '{__ne__}', got 'None' instead\">None</warning>)\n" +
                 "func32(<warning descr=\"Expected type '{__ne__}', got 'None' instead\">None</warning>)\n" +
                 "func33(<warning descr=\"Expected type '{__ne__}', got 'None' instead\">None</warning>)\n" +
                 "func34(<warning descr=\"Expected type '{__ne__}', got 'None' instead\">None</warning>)");
  }

  // PY-29704
  public void testPassingAbstractMethodResult() {
    doTestByText("import abc\n" +
                 "\n" +
                 "class Foo:\n" +
                 "    __metaclass__ = abc.ABCMeta\n" +
                 "\n" +
                 "    @abc.abstractmethod\n" +
                 "    def get_int(self):\n" +
                 "        pass\n" +
                 "\n" +
                 "    def foo(self, i):\n" +
                 "        # type: (int) -> None\n" +
                 "        print(i)\n" +
                 "\n" +
                 "    def bar(self):\n" +
                 "        self.foo(self.get_int())");
  }

  // PY-30629
  public void testIteratingOverAbstractMethodResult() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from abc import ABCMeta, abstractmethod\n" +
                         "\n" +
                         "class A(metaclass=ABCMeta):\n" +
                         "\n" +
                         "    @abstractmethod\n" +
                         "    def foo(self):\n" +
                         "        pass\n" +
                         "\n" +
                         "def something(derived: A):\n" +
                         "    for _, _ in derived.foo():\n" +
                         "        pass\n")
    );
  }

  // PY-30357
  public void testClassWithNestedAgainstStructural() {
    doTestByText("def f(cls):\n" +
                 "    print(cls.Meta)\n" +
                 "\n" +
                 "class A:\n" +
                 "    class Meta:\n" +
                 "        pass\n" +
                 "\n" +
                 "f(A)");
  }

  // PY-32205
  public void testRightShift() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("class Bin:\n" +
                         "    def __rshift__(self, other: int):\n" +
                         "        pass\n" +
                         "\n" +
                         "Bin() >> 1")
    );
  }

  // PY-32313
  public void testMatchingAgainstMultipleBoundTypeVar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing import Type, TypeVar\n" +
                         "\n" +
                         "class A:\n" +
                         "    pass\n" +
                         "\n" +
                         "class B(A):\n" +
                         "    pass\n" +
                         "\n" +
                         "class C:\n" +
                         "    pass\n" +
                         "\n" +
                         "T = TypeVar('T', A, B)\n" +
                         "\n" +
                         "def f(cls: Type[T], arg: int) -> T:\n" +
                         "    pass\n" +
                         "\n" +
                         "f(A, 1)\n" +
                         "f(B, 2)\n" +
                         "f(<warning descr=\"Expected type 'Type[T]', got 'Type[C]' instead\">C</warning>, 3)")
    );
  }

  // PY-32375
  public void testMatchingReturnAgainstBoundedTypeVar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing import TypeVar\n" +
                         "\n" +
                         "F = TypeVar('F', bound=int)\n" +
                         "\n" +
                         "def deco(func: F) -> F:\n" +
                         "    return <warning descr=\"Expected type 'F', got 'str' instead\">\"\"</warning>")
    );
  }

  // PY-35544
  public void testLessSpecificCallableAgainstMoreSpecific() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText(
        "from typing import Callable\n" +
        "\n" +
        "class MainClass:\n" +
        "    pass\n" +
        "\n" +
        "class SubClass(MainClass):\n" +
        "    pass\n" +
        "\n" +
        "def f(p: Callable[[SubClass], int]):\n" +
        "    pass\n" +
        "\n" +
        "def g(p: MainClass) -> int:\n" +
        "    pass\n" +
        "\n" +
        "f(g)"
      )
    );
  }

  // PY-35235
  public void testTypingLiteralInitialization() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Literal\n" +
                         "\n" +
                         "a: Literal[20] = 20\n" +
                         "b: Literal[30] = <warning descr=\"Expected type 'Literal[30]', got 'Literal[25]' instead\">25</warning>\n" +
                         "c: Literal[2, 3, 4] = 3")
    );
  }

  // PY-35235
  public void testTypingLiteralInitializationWithDifferentExpressions() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Literal\n" +
                         "\n" +
                         "a1: Literal[0x14] = 20\n" +
                         "a2: Literal[20] = 0x14\n" +
                         "b1: Literal[0] = <warning descr=\"Expected type 'Literal[0]', got 'Literal[False]' instead\">False</warning>\n" +
                         "b2: Literal[False] = <warning descr=\"Expected type 'Literal[False]', got 'Literal[0]' instead\">0</warning>")
    );
  }

  // PY-35235
  public void testExplicitTypingLiteralArgument() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Literal\n" +
                         "\n" +
                         "a: Literal[20] = undefined\n" +
                         "b: Literal[30] = undefined\n" +
                         "c: int = 20\n" +
                         "\n" +
                         "def foo1(p1: Literal[20]):\n" +
                         "    pass\n" +
                         "\n" +
                         "foo1(a)\n" +
                         "foo1(<warning descr=\"Expected type 'Literal[20]', got 'Literal[30]' instead\">b</warning>)\n" +
                         "foo1(<warning descr=\"Expected type 'Literal[20]', got 'int' instead\">c</warning>)\n" +
                         "\n" +
                         "def foo2(p1: int):\n" +
                         "    pass\n" +
                         "\n" +
                         "foo2(a)\n" +
                         "foo2(b)\n" +
                         "foo2(c)")
    );
  }

  // PY-35235
  public void testTypingLiteralStrings() {
    doTestByText("from typing_extensions import Literal\n" +
                 "\n" +
                 "a = undefined  # type: Literal[\"abc\"]\n" +
                 "b = undefined  # type: Literal[u\"abc\"]\n" +
                 "\n" +
                 "def foo1(p1):\n" +
                 "    # type: (Literal[\"abc\"]) -> None\n" +
                 "    pass\n" +
                 "foo1(a)\n" +
                 "foo1(<warning descr=\"Expected type 'Literal[\\\"abc\\\"]', got 'Literal[u\\\"abc\\\"]' instead\">b</warning>)\n" +
                 "\n" +
                 "def foo2(p1):\n" +
                 "    # type: (Literal[u\"abc\"]) -> None\n" +
                 "    pass\n" +
                 "foo2(<warning descr=\"Expected type 'Literal[u\\\"abc\\\"]', got 'Literal[\\\"abc\\\"]' instead\">a</warning>)\n" +
                 "foo2(b)\n" +
                 "\n" +
                 "def foo3(p1):\n" +
                 "    # type: (bytes) -> None\n" +
                 "    pass\n" +
                 "foo3(a)\n" +
                 "foo3(<warning descr=\"Expected type 'str', got 'Literal[u\\\"abc\\\"]' instead\">b</warning>)\n" +
                 "\n" +
                 "def foo4(p1):\n" +
                 "    # type: (unicode) -> None\n" +
                 "    pass\n" +
                 "foo4(a)\n" +
                 "foo4(b)\n");
  }

  // PY-35235
  public void testNegativeTypingLiterals() {
    doTestByText("from typing_extensions import Literal\n" +
                 "a = undefined  # type: Literal[-10]\n" +
                 "b = undefined  # type: Literal[-20]\n" +
                 "a = <warning descr=\"Expected type 'Literal[-10]', got 'Literal[-20]' instead\">b</warning>");
  }

  // PY-35235
  public void testDistinguishTypingLiteralsFromTypeHintOrValue() {
    doTestByText("from typing_extensions import Literal\n" +
                 "a = <warning descr=\"Expected type 'Literal[0]', got 'Type[int]' instead\">Literal[10]</warning>  # type: Literal[0]");
  }

  // PY-35235
  public void testLiteralAgainstTypeVarBoundedWithTypingLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Literal\n" +
                         "from typing import TypeVar\n" +
                         "T = TypeVar('T', Literal[\"a\"], Literal[\"b\"], Literal[\"c\"])\n" +
                         "\n" +
                         "def repeat(x: T, n: int):\n" +
                         "    return [x] * n\n" +
                         "\n" +
                         "repeat(\"c\", 2)")
    );
  }

  // PY-35235
  public void testKeywordArgumentAgainstTypingLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Literal\n" +
                         "def f(a: Literal[\"b\"]):\n" +
                         "    pass\n" +
                         "f(a='b')\n" +
                         "f(<warning descr=\"Expected type 'Literal[\\\"b\\\"]', got 'Literal['c']' instead\">a='c'</warning>)")
    );
  }

  // PY-35235
  public void testNumericMatchingAndTypingLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import Literal\n" +
                         "def expects_str(x: float) -> None: ...\n" +
                         "var: Literal[1] = 1\n" +
                         "expects_str(var)")
    );
  }

  // PY-35235
  public void testNonPlainStringAsTypingLiteralValue() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import Literal\n" +
                         "a: Literal[\"22\"] = f\"22\"\n" +
                         "b: Literal[\"22\"] = <warning descr=\"Expected type 'Literal[\\\"22\\\"]', got 'Literal[f\\\"32\\\"]' instead\">f\"32\"</warning>\n" +
                         "two = \"2\"\n" +
                         "c: Literal[\"22\"] = <warning descr=\"Expected type 'Literal[\\\"22\\\"]', got 'str' instead\">f\"2{two}\"</warning>")
    );
  }

  // PY-35235, PY-42281
  public void testExpectedTypingLiteralReturnType() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import Literal\n" +
                         "def foo() -> Literal[\"ok\"]:\n" +
                         "    return \"ok\"")
    );
  }

  // PY-33500
  public void testImplicitGenericDunderCallCallOnTypedElement() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypeVar, Generic\n" +
                         "\n" +
                         "_T = TypeVar('_T')\n" +
                         "\n" +
                         "class Callback(Generic[_T]):\n" +
                         "    def __call__(self, arg: _T):\n" +
                         "        pass\n" +
                         "\n" +
                         "def foo(cb: Callback[int]):\n" +
                         "    cb(<warning descr=\"Expected type 'int' (matched generic type '_T'), got 'str' instead\">\"42\"</warning>)")
    );
  }

  // PY-36008
  public void testTypedDictUsageAlternativeSyntax() {
    doTestByText("from typing import TypedDict\n" +
                 "Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)\n" +
                 "movie = {'name': 'Blade Runner', <warning descr=\"Extra key 'director' for TypedDict 'Movie'\">'director': 'Ridley Scott'</warning>} # type: Movie\n");
  }

  // PY-36008
  public void testTypedDictAsArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Point(TypedDict):\n" +
                         "    x: int\n" +
                         "    y: int\n" +
                         "class Movie(TypedDict):\n" +
                         "    name: str\n" +
                         "    year: int\n" +
                         "def record_movie(movie: Movie) -> None: ...\n" +
                         "record_movie({'name': <warning descr=\"Expected type 'str', got 'int' instead\">1984</warning>, 'year': 1984})\n" +
                         "record_movie(<warning descr=\"TypedDict 'Movie' has missing keys: 'name', 'year'\">{}</warning>)\n" +
                         "record_movie({'name': '1984', 'year': 1984, <warning descr=\"Extra key 'director' for TypedDict 'Movie'\">'director': 'Michael Radford'</warning>})\n" +
                         "record_movie(<warning descr=\"Expected type 'Movie', got 'Point' instead\">Point(x=123, y=321)</warning>)")
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionAsArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Movie(TypedDict):\n" +
                         "    name: str\n" +
                         "    year: int\n" +
                         "m1: Movie = dict(name='Alien', year=1979)\n" +
                         "m2 = Movie(name='Garden State', year=2004)\n" +
                         "def foo(p: int):\n" +
                         "  pass\n" +
                         "foo(m2[\"year\"])\n" +
                         "foo(<warning descr=\"Expected type 'int', got 'str' instead\">m2[\"name\"]</warning>)\n" +
                         "foo(<warning descr=\"Expected type 'int', got 'str' instead\">m1[\"name\"]</warning>)")
    );
  }

  // PY-36008
  public void testTypedDictAssignment() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Movie(TypedDict):\n" +
                         "    name: str\n" +
                         "    year: int\n" +
                         "class NotPoint(TypedDict):\n" +
                         "    x: int\n" +
                         "    y: str\n" +
                         "class Point(TypedDict):\n" +
                         "    x: int\n" +
                         "    y: int\n" +
                         "p1: Point = {'x': 0, 'y': <warning descr=\"Expected type 'int', got 'str' instead\">'a'</warning>}\n" +
                         "p2: NotPoint = {'x': <warning descr=\"Expected type 'int', got 'str' instead\">'x'</warning>, 'y': <warning descr=\"Expected type 'str', got 'int' instead\">42</warning>}\n" +
                         "p3: Point = <warning descr=\"Expected type 'Point', got 'NotPoint' instead\">p2</warning>\n" +
                         "p4: Point = <warning descr=\"TypedDict 'Point' has missing keys: 'x', 'y'\">{}</warning>\n" +
                         "p5: Point = {'x': 0, 'y': 0, <warning descr=\"Extra key 'z' for TypedDict 'Point'\">'z': 123</warning>, <warning descr=\"Extra key 'k' for TypedDict 'Point'\">'k': 6</warning>}\n" +
                         "p6: Point = <warning descr=\"TypedDict 'Point' has missing key: 'x'\">{'y': 123}</warning>\n" +
                         "p7: Movie = dict(name='Alien', year=1979)\n" +
                         "p8: Movie = dict(name='Alien', year=<warning descr=\"Expected type 'int', got 'str' instead\">'1979'</warning>)\n" +
                         "p9: Movie = dict(name='Alien', year=1979, <warning descr=\"Extra key 'director' for TypedDict 'Movie'\">director='Ridley Scott'</warning>)\n" +
                         "p10 = {'x': 'x', 'y': 42, 'z': 42}\n" +
                         "p11: Point = <warning descr=\"Expected type 'Point', got 'dict[str, str | int]' instead\">p10</warning>"
      ));
  }

  // PY-36008
  public void testTypedDictAlternativeSyntaxAssignment() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "Movie = TypedDict('Movie', {'name': str, 'year': int})\n" +
                         "m1: Movie = dict(name='Alien', year=1979)\n" +
                         "m2: Movie = dict(name='Alien', year=<warning descr=\"Expected type 'int', got 'str' instead\">'1979'</warning>)\n" +
                         "m3: Movie = typing.cast(Movie, dict(zip(['name', 'year'], ['Alien', 1979])))\n" +
                         "m4: Movie = {'name': 'Alien', 'year': <warning descr=\"Expected type 'int', got 'str' instead\">'1979'</warning>}\n" +
                         "m5 = Movie(name='Garden State', year=2004)"));
  }

  // PY-36008
  public void testTypedDictDefinition() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Employee(TypedDict):\n" +
                         "    name: str\n" +
                         "    id: int\n" +
                         "class Employee2(Employee, total=False):\n" +
                         "    director: str\n" +
                         "em = Employee2(name='John Dorian', id=1234, director='3')\n" +
                         "em2 = Employee2(name='John Dorian', id=1234, <warning descr=\"Expected type 'str', got 'int' instead\">director=3</warning>)"));
  }

  // PY-36008
  public void testTypedDictDefinitionAlternativeSyntax() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import TypedDict\n" +
                         "Movie = TypedDict(<warning descr=\"Expected type 'str', got 'int' instead\">3</warning>, <warning descr=\"Expected type 'Dict[str, Any]', got 'List[int]' instead\">[1, 2, 3]</warning>)\n" +
                         "Movie = TypedDict('Movie', {})"));
  }

  // PY-36008
  public void testTypedDictConsistency() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-36008
  public void testTypedDictKeyValueRead() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "\n" +
                         "Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)\n" +
                         "class Movie2(TypedDict, total=False):\n" +
                         "    name: str\n" +
                         "    year: int\n" +
                         "movie = Movie()\n" +
                         "movie2 = Movie2()\n" +
                         "s: str = <warning descr=\"Expected type 'str', got 'int' instead\">movie['year']</warning>\n" +
                         "s2: str = <warning descr=\"Expected type 'str', got 'int' instead\">movie2['year']</warning>\n"));
  }

  // PY-38873
  public void testTypedDictWithListField() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict, List\n" +
                         "\n" +
                         "Movie = TypedDict('Movie', {'address': List[str]}, total=False)\n" +
                         "class Movie2(TypedDict, total=False):\n" +
                         "    address: List[str]\n" +
                         "movie = Movie()\n" +
                         "movie2 = Movie2()\n" +
                         "s: str = movie['address'][0]\n" +
                         "s: str = movie2['address'][0]\n" +
                         "s: str = movie['address'][<warning descr=\"Unexpected type(s):(str)Possible type(s):(int)(slice)\">'i'</warning>]\n" +
                         "s2: str = movie2['address'][<warning descr=\"Unexpected type(s):(str)Possible type(s):(int)(slice)\">'i'</warning>]\n"));
  }

  // PY-36008
  public void testIncorrectTotalityValue() {
    doTestByText("from typing import TypedDict\n" +
                 "Movie = TypedDict(\"Movie\", {}, <warning descr=\"Expected type 'bool', got 'int' instead\">total=2</warning>)");
  }

  // PY-33548
  public void testTypeVarsChainBeforeNonTypeVarSubstitution() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText(
        "from typing import TypeVar, Mapping\n" +
        "\n" +
        "MyKT = TypeVar(\"MyKT\")\n" +
        "MyVT = TypeVar(\"MyVT\")\n" +
        "\n" +
        "class MyMapping(Mapping[MyKT, MyVT]):\n" +
        "    pass\n" +
        "\n" +
        "d: MyMapping[str, str] = undefined1\n" +
        "d.get(undefined2)\n" +
        "d.get(\"str\")\n" +
        "d.get(<warning descr=\"Expected type 'str' (matched generic type '_KT'), got 'int' instead\">1</warning>)"
      )
    );
  }

  // PY-38412
  public void testTypedDictInStub() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doMultiFileTest);
  }

  // PY-28364
  public void testDefinitionAgainstCallableInstance() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("class B:\n" +
                         "    def __call__(self, *args, **kwargs):\n" +
                         "        pass\n" +
                         "\n" +
                         "def some_fn(arg: B):\n" +
                         "    pass\n" +
                         "\n" +
                         "some_fn(<warning descr=\"Expected type 'B', got 'Type[B]' instead\">B</warning>)")
    );
  }

  // PY-29993
  public void testCallableInstanceAgainstOtherCallableInstance() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("class MyCls:\n" +
                         "    def __call__(self):\n" +
                         "        return True\n" +
                         "\n" +
                         "class DifferentCls:\n" +
                         "    def __call__(self):\n" +
                         "        return True\n" +
                         "\n" +
                         "def foo(arg: MyCls):\n" +
                         "    pass\n" +
                         "\n" +
                         "foo(MyCls())\n" +
                         "foo(<warning descr=\"Expected type 'MyCls', got 'DifferentCls' instead\">DifferentCls()</warning>)")
    );
  }

  public void testNewTypeInForeignUnstubbedFile() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
      myFixture.configureFromTempProjectFile("a.py");
      VirtualFile foreignVFile = myFixture.findFileInTempDir("b.py");
      assertNotNull(foreignVFile);
      PsiFile foreignFilePsi = PsiManager.getInstance(myFixture.getProject()).findFile(foreignVFile);
      assertNotNull(foreignFilePsi);
      assertNotParsed(foreignFilePsi);
      //noinspection ResultOfMethodCallIgnored
      foreignFilePsi.getNode();
      assertNotNull(((PsiFileImpl)foreignFilePsi).getTreeElement());
      configureInspection();
    });
  }

  // PY-42205
  public void testNonReferenceCallee() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("class CallableTest:\n" +
                         "    def __call__(self, arg=None):\n" +
                         "        pass\n" +
                         "CallableTest()(\"bad 1\")")
    );
  }

  // PY-37876
  public void testGenericCallablesInGenericClasses() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import Iterable, TypeVar, Generic\n" +
                         "T = TypeVar(\"T\")\n" +
                         "class MyClass(Generic[T]):\n" +
                         "    def __init__(self, data: Iterable[T]):\n" +
                         "        sorted(data, key=self.my_func)\n" +
                         "    def my_func(self, elem: T) -> int:\n" +
                         "        pass")
    );
  }

  // PY-37876
  public void testBoundedGenericParameterOfExpectedCallableParameter1() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import Callable, TypeVar\n" +
                         "\n" +
                         "T = TypeVar('T', bound=int)\n" +
                         "\n" +
                         "def func(c: Callable[[T], None]):\n" +
                         "    pass\n" +
                         "\n" +
                         "def accepts_anything(x: object) -> None:\n" +
                         "    pass\n" +
                         "\n" +
                         "func(accepts_anything)\n")
    );
  }

  // PY-37876
  public void testBoundedGenericParameterOfExpectedCallableParameter2() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import Callable, TypeVar\n" +
                         "\n" +
                         "T = TypeVar('T', bound=int)\n" +
                         "\n" +
                         "def func(c: Callable[[T], None]):\n" +
                         "    pass\n" +
                         "\n" +
                         "def accepts_anything(x: str) -> None:\n" +
                         "    pass\n" +
                         "\n" +
                         "func(<warning descr=\"Expected type '(Any) -> None' (matched generic type '(T) -> None'), got '(x: str) -> None' instead\">accepts_anything</warning>)\n")
    );
  }

  // PY-37876
  public void testGenericParameterOfExpectedCallableMappedByOtherArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import Callable, TypeVar\n" +
                         "\n" +
                         "T = TypeVar('T')\n" +
                         "\n" +
                         "def func(x: T, c: Callable[[T], None]) -> None:\n" +
                         "    pass\n" +
                         "\n" +
                         "def accepts_anything(x: str) -> None:\n" +
                         "    pass\n" +
                         "\n" +
                         "func(42, <warning descr=\"Expected type '(int) -> None' (matched generic type '(T) -> None'), got '(x: str) -> None' instead\">accepts_anything</warning>)")
    );
  }

  public void testCallByClass() {
    doTest();
  }

  // PY-41806
  public void testClassDefinitionAgainstProtocolDunderCall() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-41806
  public void testClassInstanceAgainstProtocolDunderCall() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-36062
  public void testModuleTypeParameter() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doMultiFileTest);
  }

  // PY-43841
  public void testPyFunctionAgainstBuiltinFunction() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-39762
  public void testOverloadsAndPureStubInSamePyiScope() {
    doMultiFileTest();
  }

  // PY-45438
  public void testFunctionAgainstCallbackProtocol() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-38065
  public void testTupleLiteralAgainstTypingLiteral() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-41268
  public void testListLiteralAgainstTypingLiteral() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-41268
  public void testSetLiteralAgainstTypingLiteral() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-41578
  public void testDictLiteralAgainstTypingLiteral() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-48798
  public void testDictLiteralInKeywordArguments() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Point(TypedDict):\n" +
                         "    x: int\n" +
                         "    y: int\n" +
                         "class Movie(TypedDict):\n" +
                         "    name: str\n" +
                         "    year: int\n" +
                         "def record_movie(movie: Movie) -> None: ...\n" +
                         "record_movie(movie={'name': 'Blade Runner', 'year': 1984})\n" +
                         "record_movie(movie=<warning descr=\"TypedDict 'Movie' has missing key: 'name'\">{'year': 1984}</warning>)\n" +
                         "record_movie(movie={'name': <warning descr=\"Expected type 'str', got 'int' instead\">1984</warning>, 'year': 1984})\n" +
                         "record_movie(movie=<warning descr=\"TypedDict 'Movie' has missing keys: 'name', 'year'\">{}</warning>)\n" +
                         "record_movie(movie={'name': '1984', 'year': 1984, <warning descr=\"Extra key 'director' for TypedDict 'Movie'\">'director': 'Michael Radford'</warning>})\n" +
                         "record_movie(movie=<warning descr=\"Expected type 'Movie', got 'Point' instead\">Point(x=123, y=321)</warning>)")
    );
  }

  // PY-48799
  public void testDictLiteralInVariable() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict\n" +
                         "class C(TypedDict):\n" +
                         "    foo: str\n" +
                         "def f(x: C) -> None:\n" +
                         "    pass\n" +
                         "y = {}\n" +
                         "z = {'foo': 'bar'}\n" +
                         "n = {\"foo\": \"\", \"quux\": 3}\n" +
                         "f(<warning descr=\"Expected type 'C', got 'dict' instead\">y</warning>)\n" +
                         "f(<warning descr=\"Expected type 'C', got 'dict[str, int | str]' instead\">n</warning>)\n" +
                         "f(z)\n" +
                         "f(x=<warning descr=\"Expected type 'C', got 'dict' instead\">y</warning>)\n" +
                         "f(x=<warning descr=\"Expected type 'C', got 'dict[str, int | str]' instead\">n</warning>)\n" +
                         "f(x=z)\n" +
                         "z2: C = <warning descr=\"Expected type 'C', got 'dict' instead\">y</warning>\n" +
                         "z2: C = <warning descr=\"Expected type 'C', got 'dict[str, int | str]' instead\">n</warning>\n" +
                         "z2: C = z" )
    );
  }

  public void testLiteralTypeInTypedDict() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing import TypedDict, Literal\n" +
                         "class Foo(TypedDict):\n" +
                         "    foo: Literal['bar']\n" +
                         "a: Foo = {'foo': 'bar'}\n" +
                         "b: Foo = {'foo': <warning descr=\"Expected type 'Literal['bar']', got 'str' instead\">'baz'</warning>}")
    );
  }

  // PY-46661
  public void testNestedTypedDict() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing_extensions import TypedDict\n" +
                         "class EasyDict(TypedDict):\n" +
                         "    a: str\n" +
                         "    b: str\n" +
                         "    c: str\n" +
                         "\n" +
                         "\n" +
                         "class NotSoHardDict(TypedDict):\n" +
                         "    a: str\n" +
                         "    b: EasyDict\n" +
                         "\n" +
                         "\n" +
                         "class HardDict(TypedDict):\n" +
                         "    a: str\n" +
                         "    d: NotSoHardDict\n" +
                         "\n" +
                         "\n" +
                         "q: HardDict = {\n" +
                         "    'a': <warning descr=\"Expected type 'str', got 'int' instead\">42</warning>,\n" +
                         "    'd': {\n" +
                         "        'b': <warning descr=\"TypedDict 'EasyDict' has missing keys: 'b', 'c'\">{'a': <warning descr=\"Expected type 'str', got 'int' instead\">42</warning>, <warning descr=\"Extra key 'd' for TypedDict 'EasyDict'\">'d': 42</warning>}</warning>,\n" +
                         "        'a': 'xx',\n" +
                         "        <warning descr=\"Extra key 'c' for TypedDict 'NotSoHardDict'\">'c': 42</warning>\n" +
                         "    },\n" +
                         "}\n" +
                         "t = {\n" +
                         "    'a': 'xx',\n" +
                         "    'd': {\n" +
                         "        0: 'zero',\n" +
                         "    }\n" +
                         "}\n" +
                         "s: HardDict = {'a': 'xx', 'd': <warning descr=\"Expected type 'NotSoHardDict', got 'dict[str, dict[int, str] | str]' instead\">t</warning>}\n" +
                         "s1: HardDict = <warning descr=\"Expected type 'HardDict', got 'dict[str, dict[int, str] | str]' instead\">t</warning>\n" +
                         "t1 = {\n" +
                         "    'a': 'xx',\n" +
                         "    'd': {\n" +
                         "        'a': 0,\n" +
                         "        'd': {}\n" +
                         "    }\n" +
                         "}\n" +
                         "s2: HardDict = {'a': 'xx', 'd': <warning descr=\"Expected type 'NotSoHardDict', got 'dict[str, dict[str, dict | int] | str]' instead\">t1</warning>}\n" +
                         "s3: HardDict = <warning descr=\"Expected type 'HardDict', got 'dict[str, dict[str, dict | int] | str]' instead\">t1</warning>\n" +
                         "s4: HardDict = <warning descr=\"TypedDict 'HardDict' has missing key: 'a'\">{\n" +
                         "    'd': {\n" +
                         "        'a': 'a',\n" +
                         "        'b': {'a': 'a', 'b': 'b', 'c': 'c'}\n" +
                         "    }\n" +
                         "}</warning>\n"
                         )
    );
  }

  // PY-50074
  public void testLiteralAgainstTypeVarWithoutBound() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }
}
