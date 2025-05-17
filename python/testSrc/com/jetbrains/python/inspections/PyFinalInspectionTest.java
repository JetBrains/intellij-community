// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.pyi.PyiFileType;
import org.jetbrains.annotations.NotNull;

public class PyFinalInspectionTest extends PyInspectionTestCase {

  // PY-79743
  public void testImportedVariableFinalReassignment() {
    doMultiFileTest();
  }

  // PY-34945
  public void testSubclassingFinalClass() {
    doMultiFileTest();

    doTestByText("""
                   from typing_extensions import final
                   @final
                   class A:
                       pass
                   class <warning descr="'A' is marked as '@final' and should not be subclassed">B</warning>(A):
                       pass""");

    doTestByText("""
                   from typing_extensions import final
                   @final
                   class A:
                       pass
                   @final
                   class B:
                       pass
                   class <warning descr="'A', 'B' are marked as '@final' and should not be subclassed">C</warning>(A, B):
                       pass""");
  }

  // PY-34945
  public void testFinalClassAsMetaclass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON30,
      () -> doTestByText("""
                           from typing_extensions import final

                           @final
                           class MT(type):
                               pass

                           class A(metaclass=MT):
                               pass""")
    );
  }

  // PY-34945
  public void testOverridingFinalMethod() {
    doMultiFileTest();

    doTestByText("""
                   from typing_extensions import final
                   class C:
                       @final
                       def method(self):
                           pass
                   class D(C):
                       def <warning descr="'aaa.C.method' is marked as '@final' and should not be overridden">method</warning>(self):
                           pass""");
  }

  // PY-34945
  public void testOverridingFinalMethodWithoutQualifiedName() {
    doTestByText("""
                   from typing_extensions import final
                   def output():
                       class Output:
                           @final
                           def foo(self):
                               pass
                       return Output
                   r = output()
                   class SubClass(r):
                       def <warning descr="'Output.foo' is marked as '@final' and should not be overridden">foo</warning>(self):
                           pass""");
  }

  // PY-34945
  public void testOverridingOverloadedFinalMethod() {
    doMultiFileTest();

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           from typing_extensions import final
                           from typing import overload

                           class A:
                               @overload
                               def foo(self, a: int) -> int: ...

                               @overload
                               def foo(self, a: str) -> str: ...

                               @final
                               def foo(self, a):
                                   return a

                           class B(A):
                               def <warning descr="'aaa.A.foo' is marked as '@final' and should not be overridden">foo</warning>(self, a):
                                   return super().foo(a)""")
    );
  }

  public void testOverridingOverloadedFinalMethod2() {
    doTestByText("""
                   from typing import final, overload
      
                   class Base:
                       @overload
                       def foo(self, x: int) -> int: ...
                   
                       @overload
                       def foo(self, x: str) -> str: ...
                   
                       @final
                       def foo(self, x: int | str) -> int | str:
                           pass
                   
                   class Derived(Base):
                       @overload
                       def foo(self, x: int) -> int: ...
                   
                       @overload
                       def foo(self, x: str) -> str: ...
                   
                       def <warning descr="'aaa.Base.foo' is marked as '@final' and should not be overridden">foo</warning>(self, x: int | str) -> int | str:
                           pass""");
  }

  public void testOverridingOverloadedFinalMethodNoImplementation() {
    final PsiFile currentFile = myFixture.configureByText(PyiFileType.INSTANCE, """
      from typing import final, overload
      
      class Base:
          @overload
          @final
          def foo(self, x: int) -> int: ...
      
          @overload
          def foo(self, x: str) -> str: ...
      
      class Derived(Base):
          @overload
          def <warning descr="'aaa.Base.foo' is marked as '@final' and should not be overridden">foo</warning>(self, x: int) -> int: ...
      
          @overload
          def foo(self, x: str) -> str: ..."""
    );
    configureInspection();
    assertSdkRootsNotParsed(currentFile);
  }

  // PY-34945
  public void testFinalNonMethodFunction() {
    doTestByText("""
                   from typing_extensions import final
                   @final
                   def <warning descr="Non-method function could not be marked as '@final'">foo</warning>():
                       pass""");
  }

  // PY-34945
  public void testOmittedAssignedValueOnModuleLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           <warning descr="'Final' name should be initialized with a value">a</warning>: Final[int]
                           <warning descr="'Final' name should be initialized with a value">b</warning>: Final
                           <warning descr="'b' is 'Final' and could not be reassigned">b</warning> = "10"
                           c: Final[str] = "10"
                           d: int
                           """)
    );
  }

  // PY-34945
  public void testOmittedAssignedValueOnClassLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           class A:
                               <warning descr="'Final' name should be initialized with a value">a</warning>: <warning descr="If assigned value is omitted, there should be an explicit type argument to 'Final'">Final</warning>
                               <warning descr="'Final' name should be initialized with a value">b</warning>: Final[int]
                               c: int
                               d: Final[int]

                               def __init__(self):
                                   self.d = 10""")
    );
  }

  // PY-34945
  public void testOmittedAssignedValueOnFunctionLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           def foo(self):
                               <warning descr="'Final' name should be initialized with a value">a</warning>: Final[int]
                               <warning descr="'Final' name should be initialized with a value">b</warning>: Final
                               c: Final[str] = "10"
                           """)
    );
  }

  // PY-34945
  public void testOmittedAssignedValueInStubOnModuleLevel() {
    final PsiFile currentFile = myFixture.configureByFile(getTestFilePath() + "i");
    configureInspection();
    assertSdkRootsNotParsed(currentFile);
  }

  // PY-34945
  public void testOmittedAssignedValueInStubOnClassLevel() {
    final PsiFile currentFile = myFixture.configureByFile(getTestFilePath() + "i");
    configureInspection();
    assertSdkRootsNotParsed(currentFile);
  }

  // PY-34945
  public void testFinalParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           from typing_extensions import Final

                           def foo(a: <warning descr="'Final' could not be used in annotations for function parameters">Final</warning>) -> None:
                               pass

                           def bar(a, <warning descr="'Final' could not be used in annotations for function parameters"># type: Final[str]</warning>
                                   ):
                               pass

                           def baz(a):
                               <warning descr="'Final' could not be used in annotations for function parameters"># type: (Final[int]) -> None</warning>
                               pass""")
    );
  }

  // PY-34945
  public void testOuterMostFinal() {
    doTestByText("""
                   from typing_extensions import Final, TypeAlias

                   a1: Final[int] = 10
                   b1: List[<warning descr="'Final' could only be used as the outermost type">Final</warning>[int]] = []

                   a2 = 10  # type: Final[int]
                   b2 = []  # type: List[<warning descr="'Final' could only be used as the outermost type">Final</warning>[int]]

                   a3: Final = 10
                   b3: List[<warning descr="'Final' could only be used as the outermost type">Final</warning>] = []

                   a4 = 10  # type: Final
                   b4 = []  # type: List[<warning descr="'Final' could only be used as the outermost type">Final</warning>]

                   A1: TypeAlias = List[<warning descr="'Final' could only be used as the outermost type">Final</warning>[int]]
                   A2: TypeAlias = 'List[<warning descr="'Final' could only be used as the outermost type">Final</warning>[int]]'
                   A3 = List[<warning descr="'Final' could only be used as the outermost type">Final</warning>[int]]  # type: TypeAlias
                   A4 = 'List[<warning descr="'Final' could only be used as the outermost type">Final</warning>[int]]'  # type: TypeAlias""");
  }

  // PY-34945
  public void testRedeclarationOnModuleLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           a: Final[int] = 10
                           print(a)
                           <warning descr="Already declared name could not be redefined as 'Final'">a</warning>: Final[str] = "10"

                           b = 10  # type: int
                           print(b)
                           <warning descr="Already declared name could not be redefined as 'Final'">b</warning> = "10"  # type: Final[str]

                           c: Final[int] = 10
                           print(c)
                           <warning descr="'c' is 'Final' and could not be reassigned">c</warning>: str = "10\"""")
    );
  }

  // PY-34945
  public void testRedeclarationOnClassLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           class A:
                               a: Final[int] = 10
                               print(a)
                               <warning descr="Already declared name could not be redefined as 'Final'">a</warning>: Final[str] = "10"

                               b = 10  # type: int
                               print(b)
                               <warning descr="Already declared name could not be redefined as 'Final'">b</warning> = "10"  # type: Final[str]

                               c: Final[int] = 10
                               print(c)
                               <warning descr="'c' is 'Final' and could not be reassigned">c</warning>: str = "10\"""")
    );
  }

  // PY-34945
  public void testRedeclarationOnFunctionLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           def foo():
                               a: Final[int] = 10
                               print(a)
                               <warning descr="Already declared name could not be redefined as 'Final'">a</warning>: Final[str] = "10"

                               b = 10  # type: int
                               print(b)
                               <warning descr="Already declared name could not be redefined as 'Final'">b</warning> = "10"  # type: Final[str]

                               c: Final[int] = 10
                               print(c)
                               <warning descr="'c' is 'Final' and could not be reassigned">c</warning>: str = "10\"""")
    );
  }

  // PY-34945
  public void testFinalInstanceAttributes() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           class A:
                               def __init__(self):
                                   self.a: Final[str] = "str"

                               def method(self):
                                   <warning descr="'Final' attribute should be declared in class body or '__init__'">self.a</warning>: Final[int] = 10
                                   <warning descr="'Final' attribute should be declared in class body or '__init__'">self.b</warning>: Final[int] = 10""")
    );
  }

  // PY-34945
  public void testSameNameClassAndInstanceLevelFinals() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText(
        """
          from typing_extensions import Final

          class A:
              a: Final[int] = 1
              b: Final[str] = "1"
              <warning descr="Either instance attribute or class attribute could be type hinted as 'Final'">c</warning>: Final[int]

              def __init__(self):
                  <warning descr="Already declared name could not be redefined as 'Final'">self.a</warning>: Final[int] = 2
                  <warning descr="'b' is 'Final' and could not be reassigned">self.b</warning> = "2"
                  <warning descr="Either instance attribute or class attribute could be type hinted as 'Final'">self.c</warning>: Final[int] = 2""")
    );
  }

  // PY-34945
  public void testModuleFinalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           a: Final[int] = 1
                           <warning descr="'a' is 'Final' and could not be reassigned">a</warning> = 2

                           b: Final[str] = "3"
                           <warning descr="'b' is 'Final' and could not be reassigned">b</warning> += "4"

                           c: Final[int] = 5
                           <warning descr="'c' is 'Final' and could not be reassigned">c</warning> += 6""")
    );
  }

  // PY-34945
  public void testImportedModuleFinalReassignment() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doMultiFileTest);
  }

  // PY-34945
  public void testClassFinalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           class A:
                               a: Final[int] = 1

                               def __init__(self):
                                   <warning descr="'a' is 'Final' and could not be reassigned">self.a</warning> = 2
                                   <warning descr="'a' is 'Final' and could not be reassigned">self.a</warning> += 2

                               def method(self):
                                   <warning descr="'a' is 'Final' and could not be reassigned">self.a</warning> = 3
                                   <warning descr="'a' is 'Final' and could not be reassigned">self.a</warning> += 3

                               @classmethod
                               def cls_method(cls):
                                   <warning descr="'a' is 'Final' and could not be reassigned">cls.a</warning> = 5
                                   <warning descr="'a' is 'Final' and could not be reassigned">cls.a</warning> += 5

                           <warning descr="'a' is 'Final' and could not be reassigned">A.a</warning> = 4
                           <warning descr="'a' is 'Final' and could not be reassigned">A.a</warning> += 4

                           class B(A):

                               @classmethod
                               def my_cls_method(cls):
                                   <warning descr="'a' is 'Final' and could not be reassigned">cls.a</warning> = 6
                                   <warning descr="'a' is 'Final' and could not be reassigned">cls.a</warning> += 6

                           <warning descr="'a' is 'Final' and could not be reassigned">B.a</warning> = 7
                           <warning descr="'a' is 'Final' and could not be reassigned">B.a</warning> += 7

                           class C(A):
                               <warning descr="'A.a' is 'Final' and could not be reassigned">a</warning> = 8
                           """)
    );
  }

  // PY-34945
  public void testImportedClassFinalReassignment() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doMultiFileTest);
  }

  // PY-34945
  public void testInstanceFinalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           class A:
                               def __init__(self):
                                   self.a: Final[int] = 1
                                   <warning descr="'a' is 'Final' and could not be reassigned">self.a</warning> += 1

                               def method(self):
                                   <warning descr="'a' is 'Final' and could not be reassigned">self.a</warning> = 2
                                   <warning descr="'a' is 'Final' and could not be reassigned">self.a</warning> = +2

                           <warning descr="'a' is 'Final' and could not be reassigned">A().a</warning> = 3
                           <warning descr="'a' is 'Final' and could not be reassigned">A().a</warning> = +3

                           class B:
                               b: Final[int]

                               def __init__(self):
                                   self.b = 1
                                   <warning descr="'b' is 'Final' and could not be reassigned">self.b</warning> += 1

                               def method(self):
                                   <warning descr="'b' is 'Final' and could not be reassigned">self.b</warning> = 2
                                   <warning descr="'b' is 'Final' and could not be reassigned">self.b</warning> += 2

                           <warning descr="'b' is 'Final' and could not be reassigned">B().b</warning> = 3
                           <warning descr="'b' is 'Final' and could not be reassigned">B().b</warning> += 3

                           class C(B):
                               def __init__(self):
                                   super().__init__()
                                   <warning descr="'B.b' is 'Final' and could not be reassigned">self.b</warning> = 4
                                   <warning descr="'B.b' is 'Final' and could not be reassigned">self.b</warning> += 4

                               def my_method(self):
                                   <warning descr="'B.b' is 'Final' and could not be reassigned">self.b</warning> = 5
                                   <warning descr="'B.b' is 'Final' and could not be reassigned">self.b</warning> += 5

                           <warning descr="'B.b' is 'Final' and could not be reassigned">C().b</warning> = 6
                           <warning descr="'B.b' is 'Final' and could not be reassigned">C().b</warning> += 6""")
    );
  }

  // PY-34945
  public void testImportedInstanceFinalReassignment() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doMultiFileTest);
  }

  // PY-34945
  public void testFunctionLevelFinalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           def foo():
                               a: Final[int] = 1
                               <warning descr="'a' is 'Final' and could not be reassigned">a</warning> = 2

                           def bar():
                               b: Final[int] = 3
                               <warning descr="'b' is 'Final' and could not be reassigned">b</warning> += 4""")
    );
  }

  // PY-34945
  public void testNonLocalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import List
                           from typing_extensions import Final

                           def outer():
                               x: Final[List[int]] = [1, 2, 3]

                               def inner():
                                   nonlocal x
                                   <warning descr="'x' is 'Final' and could not be reassigned">x</warning> = [4, 5]

                               inner()""")
    );
  }

  // PY-34945
  public void testGlobalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import List
                           from typing_extensions import Final

                           y: Final[List[int]] = [0, 1]

                           def foo():
                               global y
                               <warning descr="'y' is 'Final' and could not be reassigned">y</warning> = [4, 5]
                           """)
    );
  }

  // PY-34945
  public void testMutableReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing import List
                           from typing_extensions import Final

                           y: Final[List[int]] = [0, 1]
                           <warning descr="'y' is 'Final' and could not be reassigned">y</warning> += [4, 5]
                           """)
    );
  }

  // PY-34945
  public void testClassFinalOverriding() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           class A:
                               a: Final[int] = 1

                           class B(A):
                               <warning descr="'A.a' is 'Final' and could not be overridden">a</warning>: Final[str] = "3"
                           """)
    );
  }

  // PY-34945
  public void testImportedClassFinalOverriding() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doMultiFileTest);
  }

  // PY-34945
  public void testInstanceFinalOverriding() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           class A:
                               a: Final[int]

                               def __init__(self):
                                   self.a = 1

                           class B(A):
                               def __init__(self):
                                   super().__init__()
                                   <warning descr="'A.a' is 'Final' and could not be overridden">self.a</warning>: Final[str] = "2"

                           class C(A):
                               <warning descr="'A.a' is 'Final' and could not be overridden">a</warning>: Final[str]

                               def __init__(self):
                                   super().__init__()
                                   self.a = "3\"""")
    );
  }

  // PY-34945
  public void testImportedInstanceFinalOverriding() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doMultiFileTest);
  }

  // PY-34945
  public void testFinalInsideLoop() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           from typing_extensions import Final

                           for i in undefined:
                               if undefined:
                                   <warning descr="'Final' could not be used inside a loop">x</warning>: Final[int] = 1
                           while undefined:
                               <warning descr="'Final' could not be used inside a loop">y</warning>: Final[str] = '1'
                              \s
                           def foo():
                               for i in undefined:
                                   if undefined:
                                       <warning descr="'Final' could not be used inside a loop">x</warning>: Final[int] = 1
                               while undefined:
                                   <warning descr="'Final' could not be used inside a loop">y</warning>: Final[str] = '1'""")
    );
  }

  // PY-34945
  public void testFinalReturnValue() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           from typing_extensions import Final

                           def foo1() <warning descr="'Final' could not be used in annotation for a function return value">-> Final[int]</warning>:
                               pass

                           def foo2():
                               <warning descr="'Final' could not be used in annotation for a function return value"># type: () -> Final[int]</warning>
                               pass""")
    );
  }

  // PY-34945
  public void testMixingFinalAndAbstractDecorators() {
    doTestByText("""
                   from typing_extensions import final
                   from abc import ABC, abstractmethod

                   @final
                   class <warning descr="'Final' class could not contain abstract methods">A</warning>(ABC):
                       @abstractmethod
                       def <warning descr="'Final' class could not contain abstract methods">method</warning>(self):
                           pass
                          \s
                   class B(ABC):
                       @final
                       def method(self):
                           pass
                          \s
                   class C(ABC):
                       @final
                       @abstractmethod
                       def <warning descr="'Final' could not be mixed with abstract decorators">method</warning>(self):
                           pass""");
  }

  // PY-34945
  public void testMixingFinalMethodAndClass() {
    doTestByText("""
                   from typing_extensions import final

                   @final
                   class A:
                       @final
                       def <weak_warning descr="No need to mark method in 'Final' class as '@final'">method</weak_warning>(self):
                           pass""");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyFinalInspection.class;
  }
}
