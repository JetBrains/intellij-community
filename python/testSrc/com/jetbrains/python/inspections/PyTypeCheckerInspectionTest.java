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

  // TODO This test fails with Python 3 Typeshed stubs
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
        doTestByText("""
                       import typing
                       class Proto(typing.Protocol):
                           def function(self) -> None:
                               pass
                       class Cls:
                           def __eq__(self, other) -> 'Cls':
                               pass
                           def function(self) -> None:
                               pass
                       def method(p: Proto):
                           pass
                       method(Cls())""")
    );
  }

  // PY-28720
  public void testAgainstInvalidProtocol() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () ->
        doTestByText(
          """
            from typing import Any, Protocol
            class B:
                def foo(self):
                    ...
            class C(B, Protocol):
                def bar(self):
                    ...
            class Bar:
                def bar(self):
                    ...
            def f(x: C) -> Any:
                ...
            f(Bar())"""
        )
    );
  }

  // PY-43133
  public void testHierarchyAgainstProtocol() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText(
        """
          from typing import Protocol

          class A:
              def f1(self, x: str):
                  pass

          class B(A):
              def f2(self, y: str):
                  pass

          class P(Protocol):
              def f1(self, x: str): ...
              def f2(self, y: str): ...

          def test(p: P):
              pass

          b = B()
          test(b)"""
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
    doTestByText("""
                   def func11(value):
                       if value is not None and value != 1:
                           pass


                   def func12(value):
                       if None is not value and value != 1:
                           pass


                   def func21(value):
                       if value is None and value != 1:
                           pass


                   def func22(value):
                       if None is value and value != 1:
                           pass


                   func11(None)
                   func12(None)
                   func21(None)
                   func22(None)


                   def func31(value):
                       if value and None and value * 1:
                           pass


                   def func32(value):
                       if value is value and value * 1:
                           pass


                   def func33(value):
                       if None is None and value * 1:
                           pass


                   def func34(value):
                       a = 2
                       if a is a and value * 1:
                           pass


                   func31(<warning descr="Type 'None' doesn't have expected attribute '__mul__'">None</warning>)
                   func32(<warning descr="Type 'None' doesn't have expected attribute '__mul__'">None</warning>)
                   func33(<warning descr="Type 'None' doesn't have expected attribute '__mul__'">None</warning>)
                   func34(<warning descr="Type 'None' doesn't have expected attribute '__mul__'">None</warning>)""");
  }

  // PY-29704
  public void testPassingAbstractMethodResult() {
    doTestByText("""
                   import abc

                   class Foo:
                       __metaclass__ = abc.ABCMeta

                       @abc.abstractmethod
                       def get_int(self):
                           pass

                       def foo(self, i):
                           # type: (int) -> None
                           print(i)

                       def bar(self):
                           self.foo(self.get_int())""");
  }

  // PY-30629
  public void testIteratingOverAbstractMethodResult() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           from abc import ABCMeta, abstractmethod

                           class A(metaclass=ABCMeta):

                               @abstractmethod
                               def foo(self):
                                   pass

                           def something(derived: A):
                               for _, _ in derived.foo():
                                   pass
                           """)
    );
  }

  // PY-30357
  public void testClassWithNestedAgainstStructural() {
    doTestByText("""
                   def f(cls):
                       print(cls.Meta)

                   class A:
                       class Meta:
                           pass

                   f(A)""");
  }

  // PY-32205
  public void testRightShift() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           class Bin:
                               def __rshift__(self, other: int):
                                   pass

                           Bin() >> 1""")
    );
  }

  // PY-32313
  public void testMatchingAgainstMultipleBoundTypeVar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           from typing import Type, TypeVar

                           class A:
                               pass

                           class B(A):
                               pass

                           class C:
                               pass

                           T = TypeVar('T', A, B)

                           def f(cls: Type[T], arg: int) -> T:
                               pass

                           f(A, 1)
                           f(B, 2)
                           f(<warning descr="Expected type 'Type[T ≤: Union[A, B]]', got 'Type[C]' instead">C</warning>, 3)""")
    );
  }

  // PY-32375
  public void testMatchingReturnAgainstBoundedTypeVar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           from typing import TypeVar

                           F = TypeVar('F', bound=int)

                           def deco(func: F) -> F:
                               return <warning descr="Expected type 'F ≤: int', got 'str' instead">""</warning>""")
    );
  }

  // PY-35544
  public void testLessSpecificCallableAgainstMoreSpecific() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText(
        """
          from typing import Callable

          class MainClass:
              pass

          class SubClass(MainClass):
              pass

          def f(p: Callable[[SubClass], int]):
              pass

          def g(p: MainClass) -> int:
              pass

          f(g)"""
      )
    );
  }

  // PY-35235
  public void testTypingLiteralInitialization() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Literal

                           a: Literal[20] = 20
                           b: Literal[30] = <warning descr="Expected type 'Literal[30]', got 'Literal[25]' instead">25</warning>
                           c: Literal[2, 3, 4] = 3""")
    );
  }

  // PY-35235
  public void testTypingLiteralInitializationWithDifferentExpressions() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Literal

                           a1: Literal[0x14] = 20
                           a2: Literal[20] = 0x14
                           b1: Literal[0] = <warning descr="Expected type 'Literal[0]', got 'Literal[False]' instead">False</warning>
                           b2: Literal[False] = <warning descr="Expected type 'Literal[False]', got 'Literal[0]' instead">0</warning>""")
    );
  }

  // PY-35235
  public void testExplicitTypingLiteralArgument() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Literal

                           a: Literal[20] = undefined
                           b: Literal[30] = undefined
                           c: int = 20

                           def foo1(p1: Literal[20]):
                               pass

                           foo1(a)
                           foo1(<warning descr="Expected type 'Literal[20]', got 'Literal[30]' instead">b</warning>)
                           foo1(<warning descr="Expected type 'Literal[20]', got 'int' instead">c</warning>)

                           def foo2(p1: int):
                               pass

                           foo2(a)
                           foo2(b)
                           foo2(c)""")
    );
  }

  // PY-35235
  public void testTypingLiteralStrings() {
    doTestByText("""
                   from typing_extensions import Literal

                   a = undefined  # type: Literal["abc"]
                   b = undefined  # type: Literal[u"abc"]

                   def foo1(p1):
                       # type: (Literal["abc"]) -> None
                       pass
                   foo1(a)
                   foo1(<warning descr="Expected type 'Literal[\\"abc\\"]', got 'Literal[u\\"abc\\"]' instead">b</warning>)

                   def foo2(p1):
                       # type: (Literal[u"abc"]) -> None
                       pass
                   foo2(<warning descr="Expected type 'Literal[u\\"abc\\"]', got 'Literal[\\"abc\\"]' instead">a</warning>)
                   foo2(b)

                   def foo3(p1):
                       # type: (bytes) -> None
                       pass
                   foo3(a)
                   foo3(<warning descr="Expected type 'str', got 'Literal[u\\"abc\\"]' instead">b</warning>)

                   def foo4(p1):
                       # type: (unicode) -> None
                       pass
                   foo4(a)
                   foo4(b)
                   """);
  }

  // PY-35235
  public void testNegativeTypingLiterals() {
    doTestByText("""
                   from typing_extensions import Literal
                   a = undefined  # type: Literal[-10]
                   b = undefined  # type: Literal[-20]
                   a = <warning descr="Expected type 'Literal[-10]', got 'Literal[-20]' instead">b</warning>""");
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
      () -> doTestByText("""
                           from typing_extensions import Literal
                           from typing import TypeVar
                           T = TypeVar('T', Literal["a"], Literal["b"], Literal["c"])

                           def repeat(x: T, n: int):
                               return [x] * n

                           repeat("c", 2)""")
    );
  }

  // PY-35235
  public void testKeywordArgumentAgainstTypingLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Literal
                           def f(a: Literal["b"]):
                               pass
                           f(a='b')
                           f(<warning descr="Expected type 'Literal[\\"b\\"]', got 'Literal['c']' instead">a='c'</warning>)""")
    );
  }

  // PY-35235
  public void testNumericMatchingAndTypingLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import Literal
                           def expects_str(x: float) -> None: ...
                           var: Literal[1] = 1
                           expects_str(var)""")
    );
  }

  // PY-35235
  public void testNonPlainStringAsTypingLiteralValue() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import Literal
                           a: Literal["22"] = f"22"
                           b: Literal["22"] = <warning descr="Expected type 'Literal[\\"22\\"]', got 'Literal[f\\"32\\"]' instead">f"32"</warning>
                           two = "2"
                           c: Literal["22"] = <warning descr="Expected type 'Literal[\\"22\\"]', got 'str' instead">f"2{two}"</warning>""")
    );
  }

  // PY-35235, PY-42281
  public void testExpectedTypingLiteralReturnType() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import Literal
                           def foo() -> Literal["ok"]:
                               return "ok\"""")
    );
  }

  // PY-78964
  public void testFunctionWithTryFinally() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                   def test() -> bool:
                       try:
                           pass
                       finally:
                           pass
                   
                       return True
                   """)
    );
  }

  // PY-33500
  public void testImplicitGenericDunderCallCallOnTypedElement() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypeVar, Generic

                           _T = TypeVar('_T')

                           class Callback(Generic[_T]):
                               def __call__(self, arg: _T):
                                   pass

                           def foo(cb: Callback[int]):
                               cb(<warning descr="Expected type 'int' (matched generic type '_T'), got 'str' instead">"42"</warning>)""")
    );
  }

  // PY-36008
  public void testTypedDictUsageAlternativeSyntax() {
    doTestByText("""
                   from typing import TypedDict
                   Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
                   movie = {'name': 'Blade Runner', <warning descr="Extra key 'director' for TypedDict 'Movie'">'director': 'Ridley Scott'</warning>} # type: Movie
                   """);
    doTestByText("""
                   from typing import TypedDict
                   BadTD = TypedDict('BadTD', unknown_param=True)
                   td = {<warning descr="Extra key 'v' for TypedDict 'BadTD'">'v': 1</warning>} # type: BadTD
                   """);
  }

  // PY-36008
  public void testTypedDictAsArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict
                           class Point(TypedDict):
                               x: int
                               y: int
                           class Movie(TypedDict):
                               name: str
                               year: int
                           def record_movie(movie: Movie) -> None: ...
                           record_movie({'name': <warning descr="Expected type 'str', got 'int' instead">1984</warning>, 'year': 1984})
                           record_movie(<warning descr="TypedDict 'Movie' has missing keys: 'name', 'year'">{}</warning>)
                           record_movie({'name': '1984', 'year': 1984, <warning descr="Extra key 'director' for TypedDict 'Movie'">'director': 'Michael Radford'</warning>})
                           record_movie(<warning descr="Expected type 'Movie', got 'Point' instead">Point(x=123, y=321)</warning>)""")
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionAsArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict
                           class Movie(TypedDict):
                               name: str
                               year: int
                           m1: Movie = dict(name='Alien', year=1979)
                           m2 = Movie(name='Garden State', year=2004)
                           def foo(p: int):
                             pass
                           foo(m2["year"])
                           foo(<warning descr="Expected type 'int', got 'str' instead">m2["name"]</warning>)
                           foo(<warning descr="Expected type 'int', got 'str' instead">m1["name"]</warning>)""")
    );
  }

  // PY-36008
  public void testTypedDictAssignment() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict
                           class Movie(TypedDict):
                               name: str
                               year: int
                           class NotPoint(TypedDict):
                               x: int
                               y: str
                           class Point(TypedDict):
                               x: int
                               y: int
                           p1: Point = {'x': 0, 'y': <warning descr="Expected type 'int', got 'str' instead">'a'</warning>}
                           p2: NotPoint = {'x': <warning descr="Expected type 'int', got 'str' instead">'x'</warning>, 'y': <warning descr="Expected type 'str', got 'int' instead">42</warning>}
                           p3: Point = <warning descr="Expected type 'Point', got 'NotPoint' instead">p2</warning>
                           p4: Point = <warning descr="TypedDict 'Point' has missing keys: 'x', 'y'">{}</warning>
                           p5: Point = {'x': 0, 'y': 0, <warning descr="Extra key 'z' for TypedDict 'Point'">'z': 123</warning>, <warning descr="Extra key 'k' for TypedDict 'Point'">'k': 6</warning>}
                           p6: Point = <warning descr="TypedDict 'Point' has missing key: 'x'">{'y': 123}</warning>
                           p7: Movie = dict(name='Alien', year=1979)
                           p8: Movie = dict(name='Alien', year=<warning descr="Expected type 'int', got 'str' instead">'1979'</warning>)
                           p9: Movie = dict(name='Alien', year=1979, <warning descr="Extra key 'director' for TypedDict 'Movie'">director='Ridley Scott'</warning>)
                           p10 = {'x': 'x', 'y': 42, 'z': 42}
                           p11: Point = <warning descr="Expected type 'Point', got 'dict[str, str | int]' instead">p10</warning>"""
      ));
  }

  // PY-36008
  public void testTypedDictAlternativeSyntaxAssignment() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict
                           Movie = TypedDict('Movie', {'name': str, 'year': int})
                           m1: Movie = dict(name='Alien', year=1979)
                           m2: Movie = dict(name='Alien', year=<warning descr="Expected type 'int', got 'str' instead">'1979'</warning>)
                           m3: Movie = typing.cast(Movie, dict(zip(['name', 'year'], ['Alien', 1979])))
                           m4: Movie = {'name': 'Alien', 'year': <warning descr="Expected type 'int', got 'str' instead">'1979'</warning>}
                           m5 = Movie(name='Garden State', year=2004)"""));
  }

  // PY-36008
  public void testTypedDictDefinition() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict
                           class Employee(TypedDict):
                               name: str
                               id: int
                           class Employee2(Employee, total=False):
                               director: str
                           em = Employee2(name='John Dorian', id=1234, director='3')
                           em2 = Employee2(name='John Dorian', id=1234, <warning descr="Expected type 'str', got 'int' instead">director=3</warning>)"""));
  }

  // PY-36008
  public void testTypedDictDefinitionAlternativeSyntax() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import TypedDict
                           Movie = TypedDict(<warning descr="Expected type 'str', got 'int' instead">3</warning>, <warning descr="Expected type 'Dict[str, type]', got 'List[int]' instead">[1, 2, 3]</warning>)
                           Movie = TypedDict('Movie', {})
                           Movie = TypedDict('Movie', {'name': str})"""));
  }

  // PY-36008
  public void testTypedDictConsistency() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-36008
  public void testTypedDictKeyValueRead() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict

                           Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
                           class Movie2(TypedDict, total=False):
                               name: str
                               year: int
                           movie = Movie()
                           movie2 = Movie2()
                           s: str = <warning descr="Expected type 'str', got 'int' instead">movie['year']</warning>
                           s2: str = <warning descr="Expected type 'str', got 'int' instead">movie2['year']</warning>
                           """));
  }

  // PY-38873
  public void testTypedDictWithListField() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict, List

                           Movie = TypedDict('Movie', {'address': List[str]}, total=False)
                           class Movie2(TypedDict, total=False):
                               address: List[str]
                           movie = Movie()
                           movie2 = Movie2()
                           s: str = movie['address'][0]
                           s: str = movie2['address'][0]
                           s: str = movie['address'][<warning descr="Unexpected type(s):(str)Possible type(s):(int)(slice)">'i'</warning>]
                           s2: str = movie2['address'][<warning descr="Unexpected type(s):(str)Possible type(s):(int)(slice)">'i'</warning>]
                           """));
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
        """
          from typing import TypeVar, Mapping

          MyKT = TypeVar("MyKT")
          MyVT = TypeVar("MyVT")

          class MyMapping(Mapping[MyKT, MyVT]):
              pass

          d: MyMapping[str, str] = undefined1
          d.get(undefined2)
          d.get("str")
          d.get(<warning descr="Expected type 'str' (matched generic type '_KT'), got 'int' instead">1</warning>)"""
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
      () -> doTestByText("""
                           class B:
                               def __call__(self, *args, **kwargs):
                                   pass

                           def some_fn(arg: B):
                               pass

                           some_fn(<warning descr="Expected type 'B', got 'type[B]' instead">B</warning>)""")
    );
  }

  // PY-29993
  public void testCallableInstanceAgainstOtherCallableInstance() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           class MyCls:
                               def __call__(self):
                                   return True

                           class DifferentCls:
                               def __call__(self):
                                   return True

                           def foo(arg: MyCls):
                               pass

                           foo(MyCls())
                           foo(<warning descr="Expected type 'MyCls', got 'DifferentCls' instead">DifferentCls()</warning>)""")
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
      () -> doTestByText("""
                           class CallableTest:
                               def __call__(self, arg=None):
                                   pass
                           CallableTest()("bad 1")""")
    );
  }

  // PY-37876
  public void testGenericCallablesInGenericClasses() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import Iterable, TypeVar, Generic
                           T = TypeVar("T")
                           class MyClass(Generic[T]):
                               def __init__(self, data: Iterable[T]):
                                   sorted(data, key=self.my_func)
                               def my_func(self, elem: T) -> int:
                                   pass""")
    );
  }

  // PY-37876
  public void testBoundedGenericParameterOfExpectedCallableParameter1() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import Callable, TypeVar

                           T = TypeVar('T', bound=int)

                           def func(c: Callable[[T], None]):
                               pass

                           def accepts_anything(x: object) -> None:
                               pass

                           func(accepts_anything)
                           """)
    );
  }

  // PY-37876
  public void testBoundedGenericParameterOfExpectedCallableParameter2() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import Callable, TypeVar

                           T = TypeVar('T', bound=int)

                           def func(c: Callable[[T], None]):
                               pass

                           def accepts_anything(x: str) -> None:
                               pass

                           func(<warning descr="Expected type '(T ≤: int) -> None', got '(x: str) -> None' instead">accepts_anything</warning>)
                           """)
    );
  }

  // PY-37876
  public void testGenericParameterOfExpectedCallableMappedByOtherArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import Callable, TypeVar

                           T = TypeVar('T')

                           def func(x: T, c: Callable[[T], None]) -> None:
                               pass

                           def accepts_anything(x: str) -> None:
                               pass

                           func(42, <warning descr="Expected type '(int) -> None' (matched generic type '(T) -> None'), got '(x: str) -> None' instead">accepts_anything</warning>)""")
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
      () -> doTestByText("""
                           from typing import TypedDict
                           class Point(TypedDict):
                               x: int
                               y: int
                           class Movie(TypedDict):
                               name: str
                               year: int
                           def record_movie(movie: Movie) -> None: ...
                           record_movie(movie={'name': 'Blade Runner', 'year': 1984})
                           record_movie(movie=<warning descr="TypedDict 'Movie' has missing key: 'name'">{'year': 1984}</warning>)
                           record_movie(movie={'name': <warning descr="Expected type 'str', got 'int' instead">1984</warning>, 'year': 1984})
                           record_movie(movie=<warning descr="TypedDict 'Movie' has missing keys: 'name', 'year'">{}</warning>)
                           record_movie(movie={'name': '1984', 'year': 1984, <warning descr="Extra key 'director' for TypedDict 'Movie'">'director': 'Michael Radford'</warning>})
                           record_movie(<warning descr="Expected type 'Movie', got 'Point' instead">movie=Point(x=123, y=321)</warning>)""")
    );
  }

  // PY-48799
  public void testDictLiteralInVariable() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict
                           class C(TypedDict):
                               foo: str
                           def f(x: C) -> None:
                               pass
                           y = {}
                           z = {'foo': 'bar'}
                           n = {"foo": "", "quux": 3}
                           f(<warning descr="Expected type 'C', got 'dict[Any, Any]' instead">y</warning>)
                           f(<warning descr="Expected type 'C', got 'dict[str, str | int]' instead">n</warning>)
                           f(<warning descr="Expected type 'C', got 'dict[str, str]' instead">z</warning>)
                           f(<warning descr="Expected type 'C', got 'dict[Any, Any]' instead">x=y</warning>)
                           f(<warning descr="Expected type 'C', got 'dict[str, str | int]' instead">x=n</warning>)
                           f(<warning descr="Expected type 'C', got 'dict[str, str]' instead">x=z</warning>)
                           z2: C = <warning descr="Expected type 'C', got 'dict[Any, Any]' instead">y</warning>
                           z2: C = <warning descr="Expected type 'C', got 'dict[str, str | int]' instead">n</warning>
                           z2: C = <warning descr="Expected type 'C', got 'dict[str, str]' instead">z</warning>
                           """)
    );
  }

  public void testLiteralTypeInTypedDict() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypedDict, Literal
                           class Foo(TypedDict):
                               foo: Literal['bar']
                           a: Foo = {'foo': 'bar'}
                           b: Foo = {'foo': <warning descr="Expected type 'Literal['bar']', got 'str' instead">'baz'</warning>}""")
    );
  }

  // PY-46661
  public void testNestedTypedDict() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing_extensions import TypedDict, Literal
                           class EasyDict(TypedDict):
                               a: str
                               b: str
                               c: str
                           
                           
                           class NotSoHardDict(TypedDict):
                               a: str
                               b: EasyDict
                           
                           
                           class HardDict(TypedDict):
                               a: str
                               d: NotSoHardDict
                           
                           
                           q: HardDict = {
                               'a': <warning descr="Expected type 'str', got 'int' instead">42</warning>,
                               'd': {
                                   'b': <warning descr="TypedDict 'EasyDict' has missing keys: 'b', 'c'">{'a': <warning descr="Expected type 'str', got 'int' instead">42</warning>, <warning descr="Extra key 'd' for TypedDict 'EasyDict'">'d': 42</warning>}</warning>,
                                   'a': 'xx',
                                   <warning descr="Extra key 'c' for TypedDict 'NotSoHardDict'">'c': 42</warning>
                               },
                           }
                           t = {
                               'a': 'xx',
                               'd': {
                                   0: 'zero',
                               }
                           }
                           s: HardDict = {'a': 'xx', 'd': <warning descr="Expected type 'NotSoHardDict', got 'dict[str, str | dict[int, str]]' instead">t</warning>}
                           s1: HardDict = <warning descr="Expected type 'HardDict', got 'dict[str, str | dict[int, str]]' instead">t</warning>
                           t1 = {
                               'a': 'xx',
                               'd': {
                                   'a': 0,
                                   'd': {}
                               }
                           }
                           s2: HardDict = {'a': 'xx', 'd': <warning descr="Expected type 'NotSoHardDict', got 'dict[str, str | dict[str, int | dict[Any, Any]]]' instead">t1</warning>}
                           s3: HardDict = <warning descr="Expected type 'HardDict', got 'dict[str, str | dict[str, int | dict[Any, Any]]]' instead">t1</warning>
                           s4: HardDict = <warning descr="TypedDict 'HardDict' has missing key: 'a'">{
                               'd': {
                                   'a': 'a',
                                   'b': {'a': 'a', 'b': 'b', 'c': 'c'}
                               }
                           }</warning>
                           
                           
                           class TDWithUnionField(TypedDict):
                               i: int
                               d: Literal[""] | EasyDict
                           s5: TDWithUnionField = {'i': -1, 'd': <warning descr="Expected type 'Literal[\\"\\"] | EasyDict', got 'dict[str, str]' instead">{'a': 'a'}</warning>}
                           s6: TDWithUnionField = {'i': 7, 'd': {'a': 'a', 'b': 'b', 'c': 'c'}}
                           
                           
                           class Movie(TypedDict):
                               title: str
                               year: int
                           
                           movies1: list[Movie] = [
                               {"title": "Blade Runner", "year": 1982}, # OK
                               {"title": "The Matrix"},
                           ]
                           movies2: list[Movie] = <warning descr="Expected type 'list[Movie]', got 'list[dict[str, str]]' instead">[
                               {"title": "The Matrix"},
                           ]</warning>
                           """
      )
    );
  }

  // PY-50074
  public void testLiteralAgainstTypeVarWithoutBound() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-55092
  public void testGenericTypedDict() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import TypeVar, TypedDict, Generic
                           T = TypeVar('T')
                           T1 = TypeVar('T1')
                           class Group(TypedDict, Generic[T]):
                               key: T
                               group: list[T]
                           class GroupWithOtherKey(Group, Generic[T1]):
                               some_other_key: T1
                           group: GroupWithOtherKey[str, int] = {"key": <warning descr="Expected type 'str', got 'int' instead">1</warning>, "group": [], "some_other_key": <warning descr="Expected type 'int', got 'str' instead">''</warning>}""")
    );
  }

  // PY-27551
  public void testDunderInitAnnotatedWithNoReturn() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
                           from typing import NoReturn
                           
                           class Test:
                               def __init__(self) -> NoReturn:
                                   raise Exception()
                           
                           """)
    );
  }

  // PY-61883
  public void testTypeParameterBoundWithPEP695Syntax() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON312,
      () -> doTestByText("""
                           def foo[T: str](p: T):
                               return p
                           
                           expr = foo(<warning descr="Expected type 'T ≤: str', got 'int' instead">42</warning>)
                           """)
    );
  }

  // PY-61883
  public void testTypeParameterConstraintsWithPEP695Syntax() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON312,
      () -> doTestByText("""
                           def foo[T: (str, bool)](p: T):
                               return p
                           
                           expr = foo(<warning descr="Expected type 'T ≤: str | bool', got 'int' instead">42</warning>)
                           """)
    );
  }

  // PY-74277
  public void testPassingTypeIsCallable() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON312,
      () -> doTestByText("""
                   from typing_extensions import TypeIs
                   
                   def takes_narrower(x: int | str, narrower: Callable[[object], TypeIs[int]]):
                       if narrower(x):
                           expr1: int = x
                           #            └─ should be of `int` type
                       else:
                           expr2: str = x
                           #            └─ should be of `str` type
                   
                   def is_bool(x: object) -> TypeIs[bool]:
                       return isinstance(x, bool)

                   takes_narrower(42, is_bool)
                   """));
  }


  public void testGeneratorTypeHint() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-80427
  public void testNoneTypeType() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON312,
      () ->
        doTestByText("""
                 from types import NoneType
                 
                 x: NoneType = None
                 y: type[NoneType] = type(None)
                 z: type[None] = NoneType
                 """));
  }
}
