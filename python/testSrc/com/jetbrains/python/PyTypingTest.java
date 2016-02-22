/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tests for a type system based on mypy's typing module.
 *
 * @author vlan
 */
public class PyTypingTest extends PyTestCase {
  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.PYTHON32);
  }

  @Override
  public void tearDown() throws Exception {
    setLanguageLevel(null);
    super.tearDown();
  }

  public void testClassType() {
    doTest("Foo",
           "class Foo:" +
           "    pass\n" +
           "\n" +
           "def f(expr: Foo):\n" +
           "    pass\n");
  }

  public void testClassReturnType() {
    doTest("Foo",
           "class Foo:" +
           "    pass\n" +
           "\n" +
           "def f() -> Foo:\n" +
           "    pass\n" +
           "\n" +
           "expr = f()\n");

  }

  public void testNoneType() {
    doTest("None",
           "def f(expr: None):\n" +
           "    pass\n");
  }

  public void testNoneReturnType() {
    doTest("None",
           "def f() -> None:\n" +
           "    return 0\n" +
           "expr = f()\n");
  }

  public void testUnionType() {
    doTest("Union[int, str]",
           "from typing import Union\n" +
           "\n" +
           "def f(expr: Union[int, str]):\n" +
           "    pass\n");
  }

  public void testBuiltinList() {
    doTest("list",
           "from typing import List\n" +
           "\n" +
           "def f(expr: List):\n" +
           "    pass\n");
  }

  public void testBuiltinListWithParameter() {
    doTest("List[int]",
           "from typing import List\n" +
           "\n" +
           "def f(expr: List[int]):\n" +
           "    pass\n");
  }

  public void testBuiltinDictWithParameters() {
    doTest("Dict[str, int]",
           "from typing import Dict\n" +
           "\n" +
           "def f(expr: Dict[str, int]):\n" +
           "    pass\n");
  }

  public void testBuiltinTuple() {
    doTest("tuple",
           "from typing import Tuple\n" +
           "\n" +
           "def f(expr: Tuple):\n" +
           "    pass\n");
  }

  public void testBuiltinTupleWithParameters() {
    doTest("Tuple[int, str]",
           "from typing import Tuple\n" +
           "\n" +
           "def f(expr: Tuple[int, str]):\n" +
           "    pass\n");
  }

  public void testAnyType() {
    doTest("Any",
           "from typing import Any\n" +
           "\n" +
           "def f(expr: Any):\n" +
           "    pass\n");
  }

  public void testGenericType() {
    doTest("TypeVar('A')",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('A')\n" +
           "\n" +
           "def f(expr: T):\n" +
           "    pass\n");
  }

  public void testGenericBoundedType() {
    doTest("TypeVar('T', int, str)",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T', int, str)\n" +
           "\n" +
           "def f(expr: T):\n" +
           "    pass\n");
  }

  public void testParameterizedClass() {
    doTest("C[int]",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C(Generic[T]):\n" +
           "    def __init__(self, x: T):\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10)\n");
  }

  public void testParameterizedClassMethod() {
    doTest("int",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C(Generic[T]):\n" +
           "    def __init__(self, x: T):\n" +
           "        pass\n" +
           "    def foo(self) -> T:\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10).foo()\n");
  }

  public void testParameterizedClassInheritance() {
    doTest("int",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class B(Generic[T]):\n" +
           "    def foo(self) -> T:\n" +
           "        pass\n" +
           "class C(B[T]):\n" +
           "    def __init__(self, x: T):\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10).foo()\n");
  }

  public void testAnyStrUnification() {
    doTest("bytes",
           "from typing import AnyStr\n" +
           "\n" +
           "def foo(x: AnyStr) -> AnyStr:\n" +
           "    pass\n" +
           "\n" +
           "expr = foo(b'bar')\n");
  }

  public void testAnyStrForUnknown() {
    doTest("Union[bytes, str]",
           "from typing import AnyStr\n" +
           "\n" +
           "def foo(x: AnyStr) -> AnyStr:\n" +
           "    pass\n" +
           "\n" +
           "def bar(x):\n" +
           "    expr = foo(x)\n");
  }

  public void testCallableType() {
    doTest("(int, str) -> str",
           "from typing import Callable\n" +
           "\n" +
           "def foo(expr: Callable[[int, str], str]):\n" +
           "    pass\n");
  }

  public void testTypeInStringLiteral() {
    doTest("C",
           "class C:\n" +
           "    def foo(self, expr: 'C'):\n" +
           "        pass\n");
  }

  public void testQualifiedTypeInStringLiteral() {
    doTest("str",
           "import typing\n" +
           "\n" +
           "def foo(x: 'typing.AnyStr') -> typing.AnyStr:\n" +
           "    pass\n" +
           "\n" +
           "expr = foo('bar')\n");
  }

  public void testOptionalType() {
    doTest("Optional[int]",
           "from typing import Optional\n" +
           "\n" +
           "def foo(expr: Optional[int]):\n" +
           "    pass\n");
  }

  public void testOptionalFromDefaultNone() {
    doTest("Optional[int]",
           "def foo(expr: int = None):\n" +
           "    pass\n");
  }

  public void testFlattenUnions() {
    doTest("Union[int, str, list]",
           "from typing import Union\n" +
           "\n" +
           "def foo(expr: Union[int, Union[str, list]]):\n" +
           "    pass\n");
  }

  public void testCast() {
    doTest("str",
           "from typing import cast\n" +
           "\n" +
           "def foo(x):\n" +
           "    expr = cast(str, x)\n");
  }

  public void testComment() {
    doTest("int",
           "def foo(x):\n" +
           "    expr = x  # type: int\n");
  }

  public void testMultiAssignmentComment() {
    doTest("Tuple[int, str]",
           "def foo(x):\n" +
           "    c1, c2 = x  # type: int, str\n" +
           "    expr = c1, c2\n");
  }

  public void testForLoopComment() {
    doTest("int",
           "def foo(xs):\n" +
           "    for expr, x in xs:  # type: int, str\n" +
           "        pass\n");
  }

  public void testWithComment() {
    doTest("int",
           "def foo(x):\n" +
           "    with x as expr:  # type: int\n" +
           "        pass\n");
  }

  public void testStringLiteralInjection() {
    doTestInjectedText("class C:\n" +
                       "    def foo(self, expr: '<caret>C'):\n" +
                       "        pass\n",
                       "C");
  }

  public void testStringLiteralInjectionParameterizedType() {
    doTestInjectedText("from typing import Union, List\n" +
                       "\n" +
                       "class C:\n" +
                       "    def foo(self, expr: '<caret>Union[List[C], C]'):\n" +
                       "        pass\n",
                       "Union[List[C], C]");
  }

  // PY-15810
  public void testNoStringLiteralInjectionForNonTypingStrings() {
    doTestNoInjectedText("class C:\n" +
                         "    def foo(self, expr: '<caret>foo bar'):\n" +
                         "        pass\n");
  }

  // PY-16125
  public void testIterableForLoop() {
    doTest("int",
           "from typing import Iterable\n" +
           "\n" +
           "def foo() -> Iterable[int]:\n" +
           "    pass\n" +
           "\n" +
           "for expr in foo():\n" +
           "    pass\n");
  }

  // PY-16353
  public void testAssignedType() {
    doTest("Iterable[int]",
           "from typing import Iterable\n" +
           "\n" +
           "IntIterable = Iterable[int]\n" +
           "\n" +
           "def foo() -> IntIterable:\n" +
           "    pass\n" +
           "\n" +
           "expr = foo()\n");
  }

  // PY-16303
  public void testUnionInDocstring() {
    doTest("Optional[int]",
           "from typing import Union\n" +
           "\n" +
           "def foo(expr):\n" +
           "    '''\n" +
           "    :type expr: Union[int, None]\n" +
           "    '''\n" +
           "    pass\n");
  }

  // PY-16303
  public void testAssignedTypeInDocstring() {
    doTest("List[int]",
           "from typing import List\n" +
           "\n" +
           "IntList = List[int]\n" +
           "\n" +
           "def foo(expr):\n" +
           "    '''\n" +
           "    :type expr: IntList\n" +
           "    '''\n" +
           "    pass\n");
  }

  // PY-16303
  public void testParameterAssignedTypeInDocstring() {
    doTest("Union[int, List[int]]",
           "from typing import List, Union\n" +
           "\n" +
           "IntList = List[int]\n" +
           "\n" +
           "def foo(expr):\n" +
           "    '''\n" +
           "    :type expr: Union[int, IntList]\n" +
           "    '''\n" +
           "    pass\n");
  }

  // PY-16303
  public void testTypeVarInDocstring() {
    doTest("TypeVar('TV')",
           "from typing import TypeVar\n" +
           "\n" +
           "TV = TypeVar('TV')\n" +
           "\n" +
           "def foo(expr):\n" +
           "    '''\n" +
           "    :type expr: TV\n" +
           "    '''\n" +
           "    pass\n");
  }

  // PY-16267
  public void testGenericField() {
    doTest("str",
           "from typing import TypeVar, Generic\n" +
           "\n"                                    +
           "T = TypeVar('T', covariant=True)\n"    +
           "\n"                                    +
           "class C(Generic[T]):\n"                +
           "    def __init__(self, foo: T):\n"     +
           "        self.foo = foo\n"              +
           "\n"                                    +
           "def f() -> C[str]:\n"                  +
           "    return C('test')\n"                +
           "\n"                                    +
           "x = f()\n"                             +
           "expr = x.foo\n");
  }

  // PY-18427
  public void testConditionalType() {
    doTest("Union[int, str]",
           "if something:\n" +
           "    Type = int\n" +
           "else:\n" +
           "    Type = str\n" +
           "\n" +
           "def f(expr: Type):\n" +
           "    pass\n");
  }
  
  // PY-18254
  public void testFunctionTypeComment() {
    doTest("(x: int, args: tuple, kwargs: Dict[str, str]) -> List[bool]",
           "from typing import List\n" +
           "\n" +
           "def f(x, *args, **kwargs):\n" +
           "    # type: (int, *float, **str) -> List[bool]\n" +
           "    pass\n" +
           "\n" +
           "expr = f");
  }

  private void doTestNoInjectedText(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNull(host);
  }

  private void doTestInjectedText(@NotNull String text, @NotNull String expected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    assertFalse(files.isEmpty());
    final PsiElement injected = files.get(0).getFirst();
    assertEquals(expected, injected.getText());
  }

  @NotNull
  private PsiElement getElementAtCaret() {
    final Editor editor = myFixture.getEditor();
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(myFixture.getProject()).getPsiFile(document);
    assertNotNull(file);
    final PsiElement element = file.findElementAt(myFixture.getCaretOffset());
    assertNotNull(element);
    return element;
  }

  private void doTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.copyDirectoryToProject("typing", "");
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(),expr.getContainingFile());
    final TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType(expectedType, expr, codeAnalysis, "code analysis");
    assertType(expectedType, expr, userInitiated, "user initiated");
  }

  private static void assertType(String expectedType, PyExpression expr, TypeEvalContext context, String contextName) {
    final PyType actual = context.getType(expr);
    final String actualType = PythonDocumentationProvider.getTypeName(actual, context);
    assertEquals("Failed in " + contextName + " context", expectedType, actualType);
  }
}
