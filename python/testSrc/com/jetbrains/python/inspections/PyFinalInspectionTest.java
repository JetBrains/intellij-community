// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyFinalInspectionTest extends PyInspectionTestCase {

  // PY-34945
  public void testSubclassingFinalClass() {
    doMultiFileTest();

    doTestByText("from typing_extensions import final\n" +
                 "@final\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "class <warning descr=\"'A' is marked as '@final' and should not be subclassed\">B</warning>(A):\n" +
                 "    pass");

    doTestByText("from typing_extensions import final\n" +
                 "@final\n" +
                 "class A:\n" +
                 "    pass\n" +
                 "@final\n" +
                 "class B:\n" +
                 "    pass\n" +
                 "class <warning descr=\"'A', 'B' are marked as '@final' and should not be subclassed\">C</warning>(A, B):\n" +
                 "    pass");
  }

  // PY-34945
  public void testFinalClassAsMetaclass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON30,
      () -> doTestByText("from typing_extensions import final\n" +
                         "\n" +
                         "@final\n" +
                         "class MT(type):\n" +
                         "    pass\n" +
                         "\n" +
                         "class A(metaclass=MT):\n" +
                         "    pass")
    );
  }

  // PY-34945
  public void testOverridingFinalMethod() {
    doMultiFileTest();

    doTestByText("from typing_extensions import final\n" +
                 "class C:\n" +
                 "    @final\n" +
                 "    def method(self):\n" +
                 "        pass\n" +
                 "class D(C):\n" +
                 "    def <warning descr=\"'aaa.C.method' is marked as '@final' and should not be overridden\">method</warning>(self):\n" +
                 "        pass");
  }

  // PY-34945
  public void testOverridingFinalMethodWithoutQualifiedName() {
    doTestByText("from typing_extensions import final\n" +
                 "def output():\n" +
                 "    class Output:\n" +
                 "        @final\n" +
                 "        def foo(self):\n" +
                 "            pass\n" +
                 "    return Output\n" +
                 "r = output()\n" +
                 "class SubClass(r):\n" +
                 "    def <warning descr=\"'Output.foo' is marked as '@final' and should not be overridden\">foo</warning>(self):\n" +
                 "        pass");
  }

  // PY-34945
  public void testOverridingOverloadedFinalMethod() {
    doMultiFileTest();

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing_extensions import final\n" +
                         "from typing import overload\n" +
                         "\n" +
                         "class A:\n" +
                         "    @overload\n" +
                         "    def foo(self, a: int) -> int: ...\n" +
                         "\n" +
                         "    @overload\n" +
                         "    def foo(self, a: str) -> str: ...\n" +
                         "\n" +
                         "    @final\n" +
                         "    def foo(self, a):\n" +
                         "        return a\n" +
                         "\n" +
                         "class B(A):\n" +
                         "    def <warning descr=\"'aaa.A.foo' is marked as '@final' and should not be overridden\">foo</warning>(self, a):\n" +
                         "        return super().foo(a)")
    );
  }

  // PY-34945
  public void testFinalNonMethodFunction() {
    doTestByText("from typing_extensions import final\n" +
                 "@final\n" +
                 "def <warning descr=\"Non-method function could not be marked as '@final'\">foo</warning>():\n" +
                 "    pass");
  }

  // PY-34945
  public void testOmittedAssignedValueOnModuleLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "<warning descr=\"'Final' name should be initialized with a value\">a</warning>: Final[int]\n" +
                         "<warning descr=\"'Final' name should be initialized with a value\">b</warning>: Final\n" +
                         "<warning descr=\"'b' is 'Final' and could not be reassigned\">b</warning> = \"10\"\n" +
                         "c: Final[str] = \"10\"\n" +
                         "d: int\n")
    );
  }

  // PY-34945
  public void testOmittedAssignedValueOnClassLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "class A:\n" +
                         "    <warning descr=\"'Final' name should be initialized with a value\">a</warning>: <warning descr=\"If assigned value is omitted, there should be an explicit type argument to 'Final'\">Final</warning>\n" +
                         "    <warning descr=\"'Final' name should be initialized with a value\">b</warning>: Final[int]\n" +
                         "    c: int\n" +
                         "\n" +
                         "MY_FINAL = Final\n" +
                         "MY_FINAL_INT = Final[int]\n" +
                         "\n" +
                         "class B:\n" +
                         "    <warning descr=\"'Final' name should be initialized with a value\">с</warning>: <warning descr=\"If assigned value is omitted, there should be an explicit type argument to 'Final'\">MY_FINAL</warning>\n" +
                         "    d: MY_FINAL_INT" +
                         "\n" +
                         "    def __init__(self):\n" +
                         "        self.d = 10")
    );
  }

  // PY-34945
  public void testOmittedAssignedValueOnFunctionLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "def foo(self):\n" +
                         "    <warning descr=\"'Final' name should be initialized with a value\">a</warning>: Final[int]\n" +
                         "    <warning descr=\"'Final' name should be initialized with a value\">b</warning>: Final\n" +
                         "    c: Final[str] = \"10\"\n")
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
  public void testOverloadedFinalMethod() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing import overload\n" +
                         "from typing_extensions import final\n" +
                         "\n" +
                         "class A:\n" +
                         "    @overload\n" +
                         "    def foo(self, a: int) -> int: ...\n" +
                         "\n" +
                         "    @overload\n" +
                         "    def foo(self, a: str) -> str: ...\n" +
                         "\n" +
                         "    @final\n" +
                         "    def foo(self, a):\n" +
                         "        pass\n" +
                         "\n" +
                         "class B:\n" +
                         "    @final\n" +
                         "    @overload\n" +
                         "    def <warning descr=\"'@final' should be placed on the implementation\">foo</warning>(self, a: int) -> int: ...\n" +
                         "\n" +
                         "    @overload\n" +
                         "    def foo(self, a: str) -> str: ...\n" +
                         "\n" +
                         "    def foo(self, a):\n" +
                         "        pass\n")
    );
  }

  // PY-34945
  public void testOverloadedFinalMethodInStub() {
    final PsiFile currentFile = myFixture.configureByFile(getTestFilePath() + "i");
    configureInspection();
    assertSdkRootsNotParsed(currentFile);
  }

  // PY-34945
  public void testFinalParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "def foo(a: <warning descr=\"'Final' could not be used in annotations for function parameters\">Final</warning>) -> None:\n" +
                         "    pass\n" +
                         "\n" +
                         "def bar(a, <warning descr=\"'Final' could not be used in annotations for function parameters\"># type: Final[str]</warning>\n" +
                         "        ):\n" +
                         "    pass\n" +
                         "\n" +
                         "def baz(a):\n" +
                         "    <warning descr=\"'Final' could not be used in annotations for function parameters\"># type: (Final[int]) -> None</warning>\n" +
                         "    pass")
    );
  }

  // PY-34945
  public void testOuterMostFinal() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("from typing_extensions import Final, TypeAlias\n" +
                         "\n" +
                         "a1: Final[int] = 10\n" +
                         "b1: List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>[int]] = []\n" +
                         "\n" +
                         "a2 = 10  # type: Final[int]\n" +
                         "b2 = []  # type: List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>[int]]\n" +
                         "\n" +
                         "a3: Final = 10\n" +
                         "b3: List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>] = []\n" +
                         "\n" +
                         "a4 = 10  # type: Final\n" +
                         "b4 = []  # type: List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>]\n" +
                         "\n" +
                         "A1: TypeAlias = List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>[int]]\n" +
                         "A2: TypeAlias = 'List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>[int]]'\n" +
                         "A3 = List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>[int]]  # type: TypeAlias\n" +
                         "A4 = 'List[<warning descr=\"'Final' could only be used as the outermost type\">Final</warning>[int]]'  # type: TypeAlias")
    );
  }

  // PY-34945
  public void testRedeclarationOnModuleLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "a: Final[int] = 10\n" +
                         "print(a)\n" +
                         "<warning descr=\"Already declared name could not be redefined as 'Final'\">a</warning>" +
                         ": Final[str] = \"10\"\n" +
                         "\n" +
                         "b = 10  # type: int\n" +
                         "print(b)\n" +
                         "<warning descr=\"Already declared name could not be redefined as 'Final'\">b</warning> = \"10\"  # type: Final[str]\n" +
                         "\n" +
                         "c: Final[int] = 10\n" +
                         "print(c)\n" +
                         "<warning descr=\"'c' is 'Final' and could not be reassigned\">c</warning>: str = \"10\"")
    );
  }

  // PY-34945
  public void testRedeclarationOnClassLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "class A:\n" +
                         "    a: Final[int] = 10\n" +
                         "    print(a)\n" +
                         "    <warning descr=\"Already declared name could not be redefined as 'Final'\">a</warning>: Final[str] = \"10\"\n" +
                         "\n" +
                         "    b = 10  # type: int\n" +
                         "    print(b)\n" +
                         "    <warning descr=\"Already declared name could not be redefined as 'Final'\">b</warning> = \"10\"  # type: Final[str]\n" +
                         "\n" +
                         "    c: Final[int] = 10\n" +
                         "    print(c)\n" +
                         "    <warning descr=\"'c' is 'Final' and could not be reassigned\">c</warning>: str = \"10\"")
    );
  }

  // PY-34945
  public void testRedeclarationOnFunctionLevel() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "def foo():\n" +
                         "    a: Final[int] = 10\n" +
                         "    print(a)\n" +
                         "    <warning descr=\"Already declared name could not be redefined as 'Final'\">a</warning>: Final[str] = \"10\"\n" +
                         "\n" +
                         "    b = 10  # type: int\n" +
                         "    print(b)\n" +
                         "    <warning descr=\"Already declared name could not be redefined as 'Final'\">b</warning> = \"10\"  # type: Final[str]\n" +
                         "\n" +
                         "    c: Final[int] = 10\n" +
                         "    print(c)\n" +
                         "    <warning descr=\"'c' is 'Final' and could not be reassigned\">c</warning>: str = \"10\"")
    );
  }

  // PY-34945
  public void testFinalInstanceAttributes() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "class A:\n" +
                         "    def __init__(self):\n" +
                         "        self.a: Final[str] = \"str\"\n" +
                         "\n" +
                         "    def method(self):\n" +
                         "        <warning descr=\"'Final' attribute should be declared in class body or '__init__'\">self.a</warning>: Final[int] = 10\n" +
                         "        <warning descr=\"'Final' attribute should be declared in class body or '__init__'\">self.b</warning>: Final[int] = 10")
    );
  }

  // PY-34945
  public void testSameNameClassAndInstanceLevelFinals() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText(
        "from typing_extensions import Final\n" +
        "\n" +
        "class A:\n" +
        "    a: Final[int] = 1\n" +
        "    b: Final[str] = \"1\"\n" +
        "    <warning descr=\"Either instance attribute or class attribute could be type hinted as 'Final'\">c</warning>: Final[int]\n" +
        "\n" +
        "    def __init__(self):\n" +
        "        <warning descr=\"Already declared name could not be redefined as 'Final'\">self.a</warning>: Final[int] = 2\n" +
        "        self.b = \"2\"\n" +
        "        <warning descr=\"Either instance attribute or class attribute could be type hinted as 'Final'\">self.c</warning>: Final[int] = 2")
    );
  }

  // PY-34945
  public void testModuleFinalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "a: Final[int] = 1\n" +
                         "<warning descr=\"'a' is 'Final' and could not be reassigned\">a</warning> = 2\n" +
                         "\n" +
                         "b: Final[str] = \"3\"\n" +
                         "<warning descr=\"'b' is 'Final' and could not be reassigned\">b</warning> += \"4\"\n" +
                         "\n" +
                         "c: Final[int] = 5\n" +
                         "<warning descr=\"'c' is 'Final' and could not be reassigned\">c</warning> += 6")
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
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "class A:\n" +
                         "    a: Final[int] = 1\n" +
                         "\n" +
                         "    def __init__(self):\n" +
                         "        self.a = 2\n" +
                         "        self.a += 2\n" +
                         "\n" +
                         "    def method(self):\n" +
                         "        self.a = 3\n" +
                         "        self.a += 3\n" +
                         "\n" +
                         "    @classmethod\n" +
                         "    def cls_method(cls):\n" +
                         "        <warning descr=\"'a' is 'Final' and could not be reassigned\">cls.a</warning> = 5\n" +
                         "        <warning descr=\"'a' is 'Final' and could not be reassigned\">cls.a</warning> += 5\n" +
                         "\n" +
                         "<warning descr=\"'a' is 'Final' and could not be reassigned\">A.a</warning> = 4\n" +
                         "<warning descr=\"'a' is 'Final' and could not be reassigned\">A.a</warning> += 4\n" +
                         "\n" +
                         "class B(A):\n" +
                         "\n" +
                         "    @classmethod\n" +
                         "    def my_cls_method(cls):\n" +
                         "        <warning descr=\"'a' is 'Final' and could not be reassigned\">cls.a</warning> = 6\n" +
                         "        <warning descr=\"'a' is 'Final' and could not be reassigned\">cls.a</warning> += 6\n" +
                         "\n" +
                         "<warning descr=\"'a' is 'Final' and could not be reassigned\">B.a</warning> = 7\n" +
                         "<warning descr=\"'a' is 'Final' and could not be reassigned\">B.a</warning> += 7\n" +
                         "\n" +
                         "class C(A):\n" +
                         "    <warning descr=\"'A.a' is 'Final' and could not be reassigned\">a</warning> = 8\n")
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
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "class A:\n" +
                         "    def __init__(self):\n" +
                         "        self.a: Final[int] = 1\n" +
                         "        <warning descr=\"'a' is 'Final' and could not be reassigned\">self.a</warning> += 1\n" +
                         "\n" +
                         "    def method(self):\n" +
                         "        <warning descr=\"'a' is 'Final' and could not be reassigned\">self.a</warning> = 2\n" +
                         "        <warning descr=\"'a' is 'Final' and could not be reassigned\">self.a</warning> = +2\n" +
                         "\n" +
                         "<warning descr=\"'a' is 'Final' and could not be reassigned\">A().a</warning> = 3\n" +
                         "<warning descr=\"'a' is 'Final' and could not be reassigned\">A().a</warning> = +3\n" +
                         "\n" +
                         "class B:\n" +
                         "    b: Final[int]\n" +
                         "\n" +
                         "    def __init__(self):\n" +
                         "        self.b = 1\n" +
                         "        <warning descr=\"'b' is 'Final' and could not be reassigned\">self.b</warning> += 1\n" +
                         "\n" +
                         "    def method(self):\n" +
                         "        <warning descr=\"'b' is 'Final' and could not be reassigned\">self.b</warning> = 2\n" +
                         "        <warning descr=\"'b' is 'Final' and could not be reassigned\">self.b</warning> += 2\n" +
                         "\n" +
                         "<warning descr=\"'b' is 'Final' and could not be reassigned\">B().b</warning> = 3\n" +
                         "<warning descr=\"'b' is 'Final' and could not be reassigned\">B().b</warning> += 3\n" +
                         "\n" +
                         "class C(B):\n" +
                         "    def __init__(self):\n" +
                         "        super().__init__()\n" +
                         "        <warning descr=\"'B.b' is 'Final' and could not be reassigned\">self.b</warning> = 4\n" +
                         "        <warning descr=\"'B.b' is 'Final' and could not be reassigned\">self.b</warning> += 4\n" +
                         "\n" +
                         "    def my_method(self):\n" +
                         "        <warning descr=\"'B.b' is 'Final' and could not be reassigned\">self.b</warning> = 5\n" +
                         "        <warning descr=\"'B.b' is 'Final' and could not be reassigned\">self.b</warning> += 5\n" +
                         "\n" +
                         "<warning descr=\"'B.b' is 'Final' and could not be reassigned\">C().b</warning> = 6\n" +
                         "<warning descr=\"'B.b' is 'Final' and could not be reassigned\">C().b</warning> += 6")
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
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "def foo():\n" +
                         "    a: Final[int] = 1\n" +
                         "    <warning descr=\"'a' is 'Final' and could not be reassigned\">a</warning> = 2\n" +
                         "\n" +
                         "def bar():\n" +
                         "    b: Final[int] = 3\n" +
                         "    <warning descr=\"'b' is 'Final' and could not be reassigned\">b</warning> += 4")
    );
  }

  // PY-34945
  public void testNonLocalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import List\n" +
                         "from typing_extensions import Final\n" +
                         "\n" +
                         "def outer():\n" +
                         "    x: Final[List[int]] = [1, 2, 3]\n" +
                         "\n" +
                         "    def inner():\n" +
                         "        nonlocal x\n" +
                         "        <warning descr=\"'x' is 'Final' and could not be reassigned\">x</warning> = [4, 5]\n" +
                         "\n" +
                         "    inner()")
    );
  }

  // PY-34945
  public void testGlobalReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import List\n" +
                         "from typing_extensions import Final\n" +
                         "\n" +
                         "y: Final[List[int]] = [0, 1]\n" +
                         "\n" +
                         "def foo():\n" +
                         "    global y\n" +
                         "    <warning descr=\"'y' is 'Final' and could not be reassigned\">y</warning> = [4, 5]\n")
    );
  }

  // PY-34945
  public void testMutableReassignment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing import List\n" +
                         "from typing_extensions import Final\n" +
                         "\n" +
                         "y: Final[List[int]] = [0, 1]\n" +
                         "<warning descr=\"'y' is 'Final' and could not be reassigned\">y</warning> += [4, 5]\n")
    );
  }

  // PY-34945
  public void testClassFinalOverriding() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "class A:\n" +
                         "    a: Final[int] = 1\n" +
                         "\n" +
                         "class B(A):\n" +
                         "    <warning descr=\"'A.a' is 'Final' and could not be overridden\">a</warning>: Final[str] = \"3\"\n")
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
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "class A:\n" +
                         "    a: Final[int]\n" +
                         "\n" +
                         "    def __init__(self):\n" +
                         "        self.a = 1\n" +
                         "\n" +
                         "class B(A):\n" +
                         "    def __init__(self):\n" +
                         "        super().__init__()\n" +
                         "        <warning descr=\"'A.a' is 'Final' and could not be overridden\">self.a</warning>: Final[str] = \"2\"\n" +
                         "\n" +
                         "class C(A):\n" +
                         "    <warning descr=\"'A.a' is 'Final' and could not be overridden\">a</warning>: Final[str]\n" +
                         "\n" +
                         "    def __init__(self):\n" +
                         "        super().__init__()\n" +
                         "        self.a = \"3\"")
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
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "for i in undefined:\n" +
                         "    if undefined:\n" +
                         "        <warning descr=\"'Final' could not be used inside a loop\">x</warning>: Final[int] = 1\n" +
                         "while undefined:\n" +
                         "    <warning descr=\"'Final' could not be used inside a loop\">y</warning>: Final[str] = '1'\n" +
                         "    \n" +
                         "def foo():\n" +
                         "    for i in undefined:\n" +
                         "        if undefined:\n" +
                         "            <warning descr=\"'Final' could not be used inside a loop\">x</warning>: Final[int] = 1\n" +
                         "    while undefined:\n" +
                         "        <warning descr=\"'Final' could not be used inside a loop\">y</warning>: Final[str] = '1'")
    );
  }

  // PY-34945
  public void testFinalReturnValue() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("from typing_extensions import Final\n" +
                         "\n" +
                         "def foo1() <warning descr=\"'Final' could not be used in annotation for a function return value\">-> Final[int]</warning>:\n" +
                         "    pass\n" +
                         "\n" +
                         "def foo2():\n" +
                         "    <warning descr=\"'Final' could not be used in annotation for a function return value\"># type: () -> Final[int]</warning>\n" +
                         "    pass")
    );
  }

  // PY-34945
  public void testMixingFinalAndAbstractDecorators() {
    doTestByText("from typing_extensions import final\n" +
                 "from abc import ABC, abstractmethod\n" +
                 "\n" +
                 "@final\n" +
                 "class <warning descr=\"'Final' class could not contain abstract methods\">A</warning>(ABC):\n" +
                 "    @abstractmethod\n" +
                 "    def <warning descr=\"'Final' class could not contain abstract methods\">method</warning>(self):\n" +
                 "        pass\n" +
                 "        \n" +
                 "class B(ABC):\n" +
                 "    @final\n" +
                 "    def method(self):\n" +
                 "        pass\n" +
                 "        \n" +
                 "class C(ABC):\n" +
                 "    @final\n" +
                 "    @abstractmethod\n" +
                 "    def <warning descr=\"'Final' could not be mixed with abstract decorators\">method</warning>(self):\n" +
                 "        pass");
  }

  // PY-34945
  public void testMixingFinalMethodAndClass() {
    doTestByText("from typing_extensions import final\n" +
                 "\n" +
                 "@final\n" +
                 "class A:\n" +
                 "    @final\n" +
                 "    def <weak_warning descr=\"No need to mark method in 'Final' class as '@final'\">method</weak_warning>(self):\n" +
                 "        pass");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyFinalInspection.class;
  }
}
