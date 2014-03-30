/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyTypeTest extends PyTestCase {
  public void testTupleType() {
    doTest("str",
           "t = ('a', 2)\n" +
           "expr = t[0]");
  }

  public void testTupleAssignmentType() {
    doTest("str",
           "t = ('a', 2)\n" +
           "(expr, q) = t");
  }

  public void testBinaryExprType() {
    doTest("int",
           "expr = 1 + 2");
    doTest("str | unicode",
           "expr = '1' + '2'");
    doTest("str | unicode",
           "expr = '%s' % ('a')");
    doTest("list[int]",
           "expr = [1] + [2]");
  }

  public void testAssignmentChainBinaryExprType() {
    doTest("int",
           "class C(object):\n" +
           "    def __add__(self, other):\n" +
           "        return -1\n" +
           "c = C()\n" +
           "x = c + 'foo'\n" +
           "expr = x + 'bar'");
  }

  public void testUnaryExprType() {
    doTest("int",
           "expr = -1");
  }

  public void testTypeFromComment() {
    doTest("str",
           "expr = ''.capitalize()");
  }

  public void testUnionOfTuples() {
    doTest("(int | str, str | int)",
           "def x():\n" +
           "  if True:\n" +
           "    return (1, 'a')\n" +
           "  else:\n" +
           "    return ('a', 1)\n" +
           "expr = x()");
  }

  public void testAugAssignment() {
    doTest("int",
           "def x():\n" +
           "    count = 0\n" +
           "    count += 1\n" +
           "    return count\n" +
           "expr = x()");
  }

  public void testSetComp() {
    doTest("set",
           "expr = {i for i in range(3)}");
  }

  public void testSet() {
    doTest("set[int]",
           "expr = {1, 2, 3}");
  }

  // PY-1425
  public void testNone() {
    doTest("unknown",
           "class C:\n" +
           "    def __init__(self): self.foo = None\n" +
           "expr = C().foo");
  }

  // PY-1427
  public void testUnicodeLiteral() {  // PY-1427
    doTest("unicode",
           "expr = u'foo'");
  }

  // TODO: uncomment when we have a mock SDK for Python 3.x
  // PY-1427
  public void _testBytesLiteral() {  // PY-1427
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest("bytes",
             "expr = b'foo'");
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testPropertyType() {
    doTest("property",
           "class C:\n" +
           "    x = property(lambda self: 'foo', None, None)\n" +
           "expr = C.x\n");
  }

  public void testPropertyInstanceType() {
    doTest("str",
           "class C:\n" +
           "    x = property(lambda self: 'foo', None, None)\n" +
           "c = C()\n" +
           "expr = c.x\n");
  }

  public void testIterationType() {
    doTest("int",
           "for expr in [1, 2, 3]: pass");
  }

  public void testSubscriptType() {
    doTest("int",
           "l = [1, 2, 3]; expr = l[0]");
  }

  public void testSliceType() {
    doTest("list[int]",
           "l = [1, 2, 3]; expr = l[0:1]");
  }

  public void testExceptType() {
    doTest("ImportError",
           "try:\n" +
           "    pass\n" +
           "except ImportError, expr:\n" +
           "    pass");
  }

  public void testTypeAnno() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest("str",
             "def foo(x: str) -> list:\n" +
             "    expr = x");
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testReturnTypeAnno() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest("list",
             "def foo(x) -> list:\n" +
             "    return x\n" +
             "expr = foo(None)");
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testEpydocReturnType() {
    doTest("str",
           "def foo(*args):\n" +
           "    '''@rtype: C{str}'''\n" +
           "    return args[0]" +
           "expr = foo('')");
  }

  public void testEpydocParamType() {
    doTest("str",
           "def foo(s):\n" +
           "    '''@type s: C{str}'''\n" +
           "    expr = s");
  }

  public void testEpydocIvarType() {
    doTest("int",
           "class C:\n" +
           "    s = None\n" +
           "    '''@type: C{int}'''\n" +
           "    def foo(self):\n" +
           "        expr = self.s");
  }

  public void testRestParamType() {
    doTest("int",
           "def foo(limit):\n" +
           "  ''':param integer limit: maximum number of stack frames to show'''\n" +
           "  expr = limit");
  }

  // PY-3849
  public void testRestClassType() {
    doTest("Foo",
           "class Foo: pass\n" +
           "def foo(limit):\n" +
           "  ''':param :class:`Foo` limit: maximum number of stack frames to show'''\n" +
           "  expr = limit");
  }

  public void testRestIvarType() {
    doTest("str",
           "def foo(p):\n" +
           "    var = p.bar\n" +
           "    ''':type var: str'''\n" +
           "    expr = var");
  }

  public void testUnknownTypeInUnion() {
    doTest("int | unknown",
           "def f(c, x):\n" +
           "    if c:\n" +
           "        return 1\n" +
           "    return x\n" +
           "expr = f(1, g())\n");
  }

  public void testIsInstance() {
      doTest("str",
             "def f(c):\n" +
             "    def g():\n" +
             "        '''\n" +
             "        :rtype: int or str\n" +
             "        '''\n" +
             "    x = g()\n" +
             "    if isinstance(x, str):\n" +
             "        expr = x");
  }

  // PY-2140
  public void testNotIsInstance() {
    doTest("list",
           "def f(c):\n" +
           "    def g():\n" +
           "        '''\n" +
           "        :rtype: int or str or list\n" +
           "        '''\n" +
           "    x = g()\n" +
           "    if not isinstance(x, (str, long)):\n" +
           "        expr = x");
  }

  // PY-4383
  public void testAssertIsInstance() {
    doTest("int",
           "from unittest import TestCase\n" +
           "\n" +
           "class Test1(TestCase):\n" +
           "    def test_1(self, c):\n" +
           "        x = 1 if c else 'foo'\n" +
           "        self.assertIsInstance(x, int)\n" +
           "        expr = x\n");
  }

  // PY-4279
  public void testFieldReassignment() {
    doTest("C1",
           "class C1(object):\n" +
           "    def m1(self):\n" +
           "        pass\n" +
           "\n" +
           "class C2(object):\n" +
           "    def m2(self):\n" +
           "        pass\n" +
           "\n" +
           "class Test(object):\n" +
           "    def __init__(self, param1):\n" +
           "        self.x = param1\n" +
           "        self.x = C1()\n" +
           "        expr = self.x\n");
  }

  public void testSOEOnRecursiveCall() {
    PyExpression expr = parseExpr("def foo(x): return foo(x)\n" +
                                  "expr = foo(1)");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType actual = context.getType(expr);
    assertNull(actual);
  }

  public void testGenericConcrete() {
    PyExpression expr = parseExpr("def f(x):\n" +
                                  "    '''\n" +
                                  "    :type x: T\n" +
                                  "    :rtype: T\n" +
                                  "    '''\n" +
                                  "    return x\n" +
                                  "\n" +
                                  "expr = f(1)\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType actual = context.getType(expr);
    assertNotNull(actual);
    assertEquals("int", actual.getName());
  }

  public void testGenericConcreteMismatch() {
    PyExpression expr = parseExpr("def f(x, y):\n" +
                                  "    '''\n" +
                                  "    :type x: T\n" +
                                  "    :rtype: T\n" +
                                  "    '''\n" +
                                  "    return x\n" +
                                  "\n" +
                                  "expr = f(1)\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType actual = context.getType(expr);
    assertNotNull(actual);
    assertEquals("int", actual.getName());
  }

  // PY-5831
  public void testYieldType() {
    PyExpression expr = parseExpr("def f():\n" +
                                  "    expr = yield 2\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType actual = context.getType(expr);
    assertNull(actual);
  }

  // PY-9590
  public void testYieldParensType() {
    PyExpression expr = parseExpr("def f():\n" +
                                  "    expr = (yield 2)\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType actual = context.getType(expr);
    assertNull(actual);
  }

  // PY-6702
  public void testYieldFromType() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON33);
    try {
      doTest("str | int | float",
             "def subgen():\n" +
             "    for i in [1, 2, 3]:\n" +
             "        yield i\n" +
             "\n" +
             "def gen():\n" +
             "    yield 'foo'\n" +
             "    yield from subgen()\n" +
             "    yield 3.14\n" +
             "\n" +
             "for expr in gen():\n" +
             "    pass\n");
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testFunctionAssignment() {
    doTest("int",
           "def f():\n" +
           "    return 1\n" +
           "g = f\n" +
           "h = g\n" +
           "expr = h()\n");
  }

  public void testPropertyOfUnionType() {
    PyExpression expr = parseExpr("def f():\n" +
                                  "    '''\n" +
                                  "    :rtype: int or slice\n" +
                                  "    '''\n" +
                                  "    raise NotImplementedError\n" +
                                  "\n" +
                                  "x = f()\n" +
                                  "expr = x.start\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType actual = context.getType(expr);
    assertNull(actual);
  }

  // PY-7058
  public void testReturnTypeOfTypeForInstance() {
    PyExpression expr = parseExpr("class C(object):\n" +
                                  "    pass\n" +
                                  "\n" +
                                  "x = C()\n" +
                                  "expr = type(x)\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType type = context.getType(expr);
    assertInstanceOf(type, PyClassType.class);
    assertTrue("Got instance type instead of class type", ((PyClassType)type).isDefinition());
  }

  // PY-7058
  public void testReturnTypeOfTypeForClass() {
    PyExpression expr = parseExpr("class C(object):\n" +
                                  "    pass\n" +
                                  "\n" +
                                  "expr = type(C)\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType type = context.getType(expr);
    assertInstanceOf(type, PyClassType.class);
    assertEquals(type.getName(), "type");
  }

  // PY-7058
  public void testReturnTypeOfTypeForUnknown() {
    PyExpression expr = parseExpr("def f(x):\n" +
                                  "    expr = type(x)\n");
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType type = context.getType(expr);
    assertNull(type);
  }

  // PY-7040
  public void testInstanceAndClassAttribute() {
    doTest("int",
           "class C(object):\n" +
           "    foo = 'str1'\n" +
           "\n" +
           "    def __init__(self):\n" +
           "        self.foo = 3\n" +
           "        expr = self.foo\n");
  }

  // PY-7215
  public void testFunctionWithNestedGenerator() {
    doTest("list[int]",
           "def f():\n" +
           "    def g():\n" +
           "        yield 10\n" +
           "    return list(g())\n" +
           "\n" +
           "expr = f()\n");
  }

  public void testGeneratorNextType() {
    doTest("int",
           "def f():\n" +
           "    yield 10\n" +
           "expr = f().next()\n");
  }

  // PY-7020
  public void testListComprehensionType() {
    final PyExpression expr = parseExpr("expr = [str(x) for x in range(10)]\n");
    final TypeEvalContext context = getTypeEvalContext(expr);
    final PyType type = context.getType(expr);
    assertNotNull(type);
    assertInstanceOf(type, PyCollectionType.class);
    assertEquals(type.getName(), "list");
    final PyCollectionType collectionType = (PyCollectionType)type;
    final PyType elementType = collectionType.getElementType(context);
    assertNotNull(elementType);
    assertEquals(elementType.getName(), "str");
  }

  // PY-7021
  public void testGeneratorComprehensionType() {
    final PyExpression expr = parseExpr("expr = (str(x) for x in range(10))\n");
    final TypeEvalContext context = getTypeEvalContext(expr);
    final PyType type = context.getType(expr);
    assertNotNull(type);
    assertInstanceOf(type, PyCollectionType.class);
    assertEquals(type.getName(), "__generator");
    final PyCollectionType collectionType = (PyCollectionType)type;
    final PyType elementType = collectionType.getElementType(context);
    assertNotNull(elementType);
    assertEquals(elementType.getName(), "str");
  }

  // EA-40207
  public void testRecursion() {
    doTest("list[list]",
           "def f():\n" +
           "    return [f()]\n" +
           "expr = f()\n");
  }

  // PY-5084
  public void testIfIsInstanceElse() {
    doTest("str",
           "def test(c):\n" +
           "    x = 'foo' if c else 42\n" +
           "    if isinstance(x, int):\n" +
           "        print(x)\n" +
           "    else:\n" +
           "        expr = x\n");
  }

  // PY-5614
  public void testUnknownReferenceTypeAttribute() {
    doTest("str",
           "def f(x):\n" +
           "    if isinstance(x.foo, str):\n" +
           "        expr = x.foo\n");
  }

  // PY-5614
  public void testUnknownTypeAttribute() {
    doTest("str",
           "class C(object):\n" +
           "    def __init__(self, foo):\n" +
           "        self.foo = foo\n" +
           "    def f(self):\n" +
           "        if isinstance(self.foo, str):\n" +
           "            expr = self.foo\n");
  }

  // PY-5614
  public void testKnownTypeAttribute() {
    doTest("str",
           "class C(object):\n" +
           "    def __init__(self):\n" +
           "        self.foo = 42\n" +
           "    def f(self):\n" +
           "        if isinstance(self.foo, str):\n" +
           "            expr = self.foo\n");
  }

  // PY-5614
  public void testNestedUnknownReferenceTypeAttribute() {
    doTest("str",
           "def f(x):\n" +
           "    if isinstance(x.foo.bar, str):\n" +
           "        expr = x.foo.bar\n");

  }

  // PY-7063
  public void testDefaultParameterValue() {
    doTest("int",
           "def f(x, y=0):\n" +
           "    return y\n" +
           "expr = f(a, b)\n");
  }

  public void testLogicalAndExpression() {
    doTest("str | int",
           "expr = 'foo' and 2");
  }

  public void testLogicalNotExpression() {
    doTest("bool",
           "expr = not 'hello'");
  }

  // PY-7063
  public void testDefaultParameterIgnoreNone() {
    final PyExpression expr = parseExpr("def f(x=None):\n" +
                                        "    expr = x\n");
    final TypeEvalContext context = getTypeEvalContext(expr);
    final PyType type = context.getType(expr);
    assertNull(type);
  }

  public void testParameterFromUsages() {
    doTest("int | str | unknown",
           "def foo(bar):\n" +
           "    expr = bar\n" +
           "def use_foo(x):\n" +
           "    foo(x)\n" +
           "    foo(3)\n" +
           "    foo('bar')\n");
  }

  public void testUpperBoundGeneric() {
    doTest("int | str",
           "def foo(x):\n" +
           "    '''\n" +
           "    :type x: T <= int or str\n" +
           "    :rtype: T\n" +
           "    '''\n" +
           "def bar(x):\n" +
           "    expr = foo(x)\n");
  }

  public void testIterationTypeFromGetItem() {
    doTest("int",
           "class C(object):\n" +
           "    def __getitem__(self, index):\n" +
           "        return 0\n" +
           "    def __len__(self):\n" +
           "        return 10\n" +
           "for expr in C():\n" +
           "    pass\n");
  }

  public void testFunctionTypeAsUnificationArgument() {
    doTest("int",
           "def map2(f, xs):\n" +
           "    '''\n" +
           "    :type f: (T) -> V | None\n" +
           "    :type xs: collections.Iterable[T] | bytes | unicode\n" +
           "    :rtype: list[V] | bytes | unicode\n" +
           "    '''\n" +
           "    pass\n" +
           "\n" +
           "expr = map2(lambda x: 10, ['1', '2', '3'])[0]\n");
  }

  public void testFunctionTypeAsUnificationResult() {
    doTest("int",
           "def f(x):\n" +
           "    '''\n" +
           "    :type x: T\n" +
           "    :rtype: () -> T\n" +
           "    '''\n" +
           "    pass\n" +
           "\n" +
           "g = f(10)\n" +
           "expr = g()\n");
  }

  public void testUnionIteration() {
    final String text = "def f(c):\n" +
                        "    if c < 0:\n" +
                        "        return [1, 2, 3]\n" +
                        "    elif c == 0:\n" +
                        "        return 0.0\n" +
                        "    else:\n" +
                        "        return 'foo'\n" +
                        "\n" +
                        "def g(c):\n" +
                        "    for expr in f(c):\n" +
                        "        pass\n";
    final PyExpression expr = parseExpr(text);
    final TypeEvalContext context = getTypeEvalContext(expr);
    final PyType type = context.getType(expr);
    assertInstanceOf(type, PyUnionType.class);
    assertTrue(PyTypeChecker.match(PyTypeParser.getTypeByName(expr, "int"), type, context));
    assertTrue(PyTypeChecker.match(PyTypeParser.getTypeByName(expr, "str"), type, context));
    assertTrue(PyTypeChecker.isUnknown(type));
  }

  public void testParameterOfFunctionTypeAndReturnValue() {
    doTest("int",
           "def func(f):\n" +
           "    '''\n" +
           "    :type f: (unknown) -> str\n" +
           "    '''\n" +
           "    return 1\n" +
           "\n" +
           "expr = func(foo)\n");
  }

  // PY-6584
  public void testClassAttributeTypeInClassDocStringViaClass() {
    doTest("int",
           "class C(object):\n" +
           "    '''\n" +
           "    :type foo: int\n" +
           "    '''\n" +
           "    foo = None\n" +
           "\n" +
           "expr = C.foo\n");
  }

  // PY-6584
  public void testClassAttributeTypeInClassDocStringViaInstance() {
    doTest("int",
           "class C(object):\n" +
           "    '''\n" +
           "    :type foo: int\n" +
           "    '''\n" +
           "    foo = None\n" +
           "\n" +
           "expr = C().foo\n");
  }

  // PY-6584
  public void testInstanceAttributeTypeInClassDocString() {
    doTest("int",
           "class C(object):\n" +
           "    '''\n" +
           "    :type foo: int\n" +
           "    '''\n" +
           "    def __init__(self, bar):\n" +
           "        self.foo = bar\n" +
           "\n" +
           "def f(x):\n" +
           "    expr = C(x).foo\n");
  }

  public void testOpenDefault() {
    doTest("FileIO[str]",
           "expr = open('foo')\n");
  }

  public void testOpenText() {
    doTest("FileIO[str]",
           "expr = open('foo', 'r')\n");
  }

  public void testOpenBinary() {
    doTest("FileIO[str]",
           "expr = open('foo', 'rb')\n");
  }

  public void testIoOpenDefault() {
    doTest("TextIOWrapper[unicode]",
           "import io\n" +
           "expr = io.open('foo')\n");
  }

  public void testIoOpenText() {
    doTest("TextIOWrapper[unicode]",
           "import io\n" +
           "expr = io.open('foo', 'r')\n");
  }

  public void testIoOpenBinary() {
    doTest("FileIO[str]",
           "import io\n" +
           "expr = io.open('foo', 'rb')\n");
  }

  public void testNoResolveToFunctionsInTypes() {
    doTest("C | unknown",
           "class C(object):\n" +
           "    def bar(self):\n" +
           "        pass\n" +
           "\n" +
           "def foo(x):\n" +
           "    '''\n" +
           "    :type x: C | C.bar | foo\n" +
           "    '''\n" +
           "    expr = x\n");
  }

  public void testIsInstanceExpressionResolvedToTuple() {
    doTest("str | unicode",
           "string_types = str, unicode\n" +
           "\n" +
           "def f(x):\n" +
           "    if isinstance(x, string_types):\n" +
           "        expr = x\n");
  }

  public void testIsInstanceInConditionalExpression() {
    doTest("str | int",
           "def f(x):\n" +
           "    expr = x if isinstance(x, str) else 10\n");
  }

  // PY-9334
  public void testIterateOverListOfNestedTuples() {
    doTest("str",
           "def f():\n" +
           "    for i, (expr, v) in [(0, ('foo', []))]:\n" +
           "        print(expr)\n");
  }

  // PY-8953
  public void testSelfInDocString() {
    doTest("int",
           "class C(object):\n" +
           "    def foo(self):\n" +
           "        '''\n" +
           "        :type self: int\n" +
           "        '''\n" +
           "        expr = self\n");
  }

  // PY-9605
  public void testPropertyReturnsCallable() {
    doTest("() -> int",
           "class C(object):\n" +
           "    @property\n" +
           "    def foo(self):\n" +
           "        return lambda: 0\n" +
           "\n" +

           "c = C()\n" +
           "expr = c.foo\n");
  }

  public void testIterNext() {
    doTest("int",
           "xs = [1, 2, 3]\n" +
           "expr = iter(xs).next()\n");
  }

  private static TypeEvalContext getTypeEvalContext(@NotNull PyExpression element) {
    return TypeEvalContext.userInitiated(element.getContainingFile()).withTracing();
  }

  private PyExpression parseExpr(String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    return myFixture.findElementByText("expr", PyExpression.class);
  }

  private void doTest(final String expectedType, final String text) {
    PyExpression expr = parseExpr(text);
    TypeEvalContext context = getTypeEvalContext(expr);
    PyType actual = context.getType(expr);
    final String actualType = PythonDocumentationProvider.getTypeName(actual, context);
    assertEquals(expectedType, actualType);
  }
}
