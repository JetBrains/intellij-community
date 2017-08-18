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
package com.jetbrains.python;

import com.google.common.collect.ImmutableList;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyTypeTest extends PyTestCase {
  /**
   * Call of union returns union of all callable types in this union
   */
  public void testCallableInUnion() {
    doTest("str",
           "import random\n" +
           "def spam():\n" +
           "    return \"D\"\n" +
           "class Eggs:\n" +
           "    pass\n" +
           "class Eggs2:\n" +
           "    pass\n" +
           "dd = spam if random.randint != 42 else Eggs2()\n" +
           "var = dd if random.randint != 42 else dd\n" +
           "expr = var()");
  }

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
    doTest("str",
           "expr = '1' + '2'");
    doTest("str",
           "expr = '%s' % ('a')");
    doTest("List[int]",
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
    doTest("Union[Tuple[int, str], Tuple[str, int]]",
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
    doTest("Set[int]",
           "expr = {1, 2, 3}");
  }

  // PY-1425
  public void testNone() {
    doTest("Any",
           "class C:\n" +
           "    def __init__(self): self.foo = None\n" +
           "expr = C().foo");
  }

  // PY-1427
  public void testUnicodeLiteral() {  // PY-1427
    doTest("unicode",
           "expr = u'foo'");
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

  public void testListSliceType() {
    doTest("List[int]",
           "l = [1, 2, 3]; expr = l[0:1]");
  }

  public void testTupleSliceType() {
    doTest("tuple",
           "l = (1, 2, 3); expr = l[0:1]");
  }

  // PY-18560
  public void testCustomSliceType() {
    doTest(
      "int",
      "class RectangleFactory(object):\n" +
      "    def __getitem__(self, item):\n" +
      "        return 1\n" +
      "factory = RectangleFactory()\n" +
      "expr = factory[:]"
    );
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
    doTest("Union[int, Any]",
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

  // PY-20679
  public void testIsInstanceViaTrue() {
    doTest("str",
           "a = None\n" +
           "if isinstance(a, str) is True:\n" +
           "    expr = a\n" +
           "raise TypeError('Invalid type')");

    doTest("str",
           "a = None\n" +
           "if True is isinstance(a, str):\n" +
           "    expr = a\n" +
           "raise TypeError('Invalid type')");
  }

  // PY-20679
  public void testIsInstanceViaFalse() {
    doTest("str",
           "a = None\n" +
           "if isinstance(a, str) is not False:\n" +
           "    expr = a\n" +
           "raise TypeError('Invalid type')");

    doTest("str",
           "a = None\n" +
           "if False is not isinstance(a, str):\n" +
           "    expr = a\n" +
           "raise TypeError('Invalid type')");

    doTest("str",
           "a = None\n" +
           "if not isinstance(a, str) is False:\n" +
           "    expr = a\n" +
           "raise TypeError('Invalid type')");

    doTest("str",
           "a = None\n" +
           "if not False is isinstance(a, str):\n" +
           "    expr = a\n" +
           "raise TypeError('Invalid type')");
  }

  // PY-20679
  public void testNotIsInstanceViaTrue() {
    doTest("str",
           "a = None\n" +
           "if not isinstance(a, str) is True:\n" +
           "    raise TypeError('Invalid type')\n" +
           "expr = a");

    doTest("str",
           "a = None\n" +
           "if not True is isinstance(a, str):\n" +
           "    raise TypeError('Invalid type')\n" +
           "expr = a");

    doTest("str",
           "a = None\n" +
           "if isinstance(a, str) is not True:\n" +
           "    raise TypeError('Invalid type')\n" +
           "expr = a");

    doTest("str",
           "a = None\n" +
           "if True is not isinstance(a, str):\n" +
           "    raise TypeError('Invalid type')\n" +
           "expr = a");
  }

  // PY-20679
  public void testNotIsInstanceViaFalse() {
    doTest("str",
           "a = None\n" +
           "if isinstance(a, str) is False:\n" +
           "    raise TypeError('Invalid type')\n" +
           "expr = a");

    doTest("str",
           "a = None\n" +
           "if False is isinstance(a, str):\n" +
           "    raise TypeError('Invalid type')\n" +
           "expr = a");
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
    doTest("Any", "def foo(x): return foo(x)\n" +
                  "expr = foo(1)");
  }

  public void testGenericConcrete() {
    doTest("int", "def f(x):\n" +
                  "    '''\n" +
                  "    :type x: T\n" +
                  "    :rtype: T\n" +
                  "    '''\n" +
                  "    return x\n" +
                  "\n" +
                  "expr = f(1)\n");
  }

  public void testGenericConcreteMismatch() {
    doTest("int", "def f(x, y):\n" +
                  "    '''\n" +
                  "    :type x: T\n" +
                  "    :rtype: T\n" +
                  "    '''\n" +
                  "    return x\n" +
                  "\n" +
                  "expr = f(1)\n");
  }

  // PY-5831
  public void testYieldType() {
    doTest("Any", "def f():\n" +
                 "    expr = yield 2\n");
  }

  // PY-9590
  public void testYieldParensType() {
    doTest("Any", "def f():\n" +
                  "    expr = (yield 2)\n");
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
    doTest("Optional[int]", "def f():\n" +
                  "    '''\n" +
                  "    :rtype: int or slice\n" +
                  "    '''\n" +
                  "    raise NotImplementedError\n" +
                  "\n" +
                  "x = f()\n" +
                  "expr = x.start\n");
  }

  public void testUndefinedPropertyOfUnionType() {
    doTest("Any", "x = 42 if True else 'spam'\n" +
                  "expr = x.foo\n");
  }

  // PY-7058
  public void testReturnTypeOfTypeForInstance() {
    PyExpression expr = parseExpr("class C(object):\n" +
                                  "    pass\n" +
                                  "\n" +
                                  "x = C()\n" +
                                  "expr = type(x)\n");
    assertNotNull(expr);
    for (TypeEvalContext context : getTypeEvalContexts(expr)) {
      PyType type = context.getType(expr);
      assertInstanceOf(type, PyClassType.class);
      assertTrue("Got instance type instead of class type", ((PyClassType)type).isDefinition());
    }
  }

  // PY-7058
  public void testReturnTypeOfTypeForClass() {
    doTest("type", "class C(object):\n" +
                   "    pass\n" +
                   "\n" +
                   "expr = type(C)\n");
  }

  // PY-7058
  public void testReturnTypeOfTypeForUnknown() {
    doTest("Any", "def f(x):\n" +
                  "    expr = type(x)\n");
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
    doTest("List[int]",
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

  public void testGeneratorFunctionType() {
    doTest("Generator[str, Any, int]",
           "def f():\n" +
           "    yield 'foo'\n" +
           "    return 0\n" +
           "\n" +
           "expr = f()\n");
  }

  // PY-7020
  public void testListComprehensionType() {
    doTest("List[str]", "expr = [str(x) for x in range(10)]\n");
  }

  // PY-7021
  public void testGeneratorComprehensionType() {
    doTest("Generator[str, Any, None]", "expr = (str(x) for x in range(10))\n");
  }

  // PY-7021
  public void testIterOverGeneratorComprehension() {
    doTest("str",
           "xs = (str(x) for x in range(10))\n" +
           "for expr in xs:\n" +
           "    pass\n");
  }

  // EA-40207
  public void testRecursion() {
    doTest("list",
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
    doTest("Union[str, int]",
           "expr = 'foo' and 2");
  }

  public void testLogicalNotExpression() {
    doTest("bool",
           "expr = not 'hello'");
  }

  // PY-7063
  public void testDefaultParameterIgnoreNone() {
    doTest("Any", "def f(x=None):\n" +
                  "    expr = x\n");
  }

  public void testParameterFromUsages() {
    final String text = "def foo(bar):\n" +
                        "    expr = bar\n" +
                        "def use_foo(x):\n" +
                        "    foo(x)\n" +
                        "    foo(3)\n" +
                        "    foo('bar')\n";
    final PyExpression expr = parseExpr(text);
    assertNotNull(expr);
    doTest("Union[Union[int, str], Any]", expr, TypeEvalContext.codeCompletion(expr.getProject(), expr.getContainingFile()));
  }

  public void testUpperBoundGeneric() {
    doTest("Union[int, str]",
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
    doTest("Union[List[int], str, unicode]",
           "def map2(f, xs):\n" +
           "    '''\n" +
           "    :type f: (T) -> V | None\n" +
           "    :type xs: collections.Iterable[T] | str | unicode\n" +
           "    :rtype: list[V] | str | unicode\n" +
           "    '''\n" +
           "    pass\n" +
           "\n" +
           "expr = map2(lambda x: 10, ['1', '2', '3'])\n");
  }

  public void testFunctionTypeAsUnificationArgumentWithSubscription() {
    doTest("Union[int, str, unicode]",
           "def map2(f, xs):\n" +
           "    '''\n" +
           "    :type f: (T) -> V | None\n" +
           "    :type xs: collections.Iterable[T] | str | unicode\n" +
           "    :rtype: list[V] | str | unicode\n" +
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
    doTest("Union[Union[int, str], Any]",
           "def f(c):\n" +
           "    if c < 0:\n" +
           "        return [1, 2, 3]\n" +
           "    elif c == 0:\n" +
           "        return 0.0\n" +
           "    else:\n" +
           "        return 'foo'\n" +
           "\n" +
           "def g(c):\n" +
           "    for expr in f(c):\n" +
           "        pass\n");
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
    doTest("file",
           "expr = open('foo')\n");
  }

  public void testOpenText() {
    doTest("file",
           "expr = open('foo', 'r')\n");
  }

  public void testOpenBinary() {
    doTest("file",
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
    doTest("Union[C, Any]",
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
    doTest("Union[str, unicode]",
           "string_types = str, unicode\n" +
           "\n" +
           "def f(x):\n" +
           "    if isinstance(x, string_types):\n" +
           "        expr = x\n");
  }

  public void testIsInstanceInConditionalExpression() {
    doTest("Union[str, int]",
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

  // PY-10967
  public void testDefaultTupleParameterMember() {
    doTest("int",
           "def foo(xs=(1, 2)):\n" +
           "  expr, foo = xs\n");
  }

  // PY-19826
  public void testListFromTuple() {
    doTest("List[Union[str, int]]",
           "expr = list(('1', 2, 3))");
  }

  public void testDictFromTuple() {
    doTest("Dict[Union[str, int], Union[str, int]]",
           "expr = dict((('1', 1), (2, 2), (3, '3')))");
  }

  public void testSetFromTuple() {
    doTest("Set[Union[str, int]]",
           "expr = set(('1', 2, 3))");
  }

  public void testTupleFromTuple() {
    doTest("Tuple[str, int, int]",
           "expr = tuple(('1', 2, 3))");
  }

  public void testTupleFromList() {
    doTest("Tuple[Union[str, int], ...]",
           "expr = tuple(['1', 2, 3])");
  }

  public void testTupleFromDict() {
    doTest("Tuple[Union[str, int], ...]",
           "expr = tuple({'1': 'a', 2: 'b', 3: 4})");
  }

  public void testTupleFromSet() {
    doTest("Tuple[Union[str, int], ...]",
           "expr = tuple({'1', 2, 3})");
  }

  public void testHomogeneousTupleSubstitution() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        myFixture.copyDirectoryToProject("typing", "");

        doTest("Tuple[int, ...]",
               "from typing import TypeVar, Tuple\n" +
               "T = TypeVar('T')\n" +
               "def foo(i: T) -> Tuple[T, ...]:\n" +
               "    pass\n" +
               "expr = foo(5)");
      }
    );
  }

  public void testHeterogeneousTupleSubstitution() {
    doTest("tuple[int, int]",
           "def foo(i):\n" +
           "    \"\"\"\n" +
           "    :type i: T\n" +
           "    :rtype: tuple[T, T]\n" +
           "    \"\"\"\n" +
           "    pass\n" +
           "expr = foo(5)");
  }

  public void testUnknownTupleSubstitution() {
    doTest("tuple",
           "def foo(i):\n" +
           "    \"\"\"\n" +
           "    :type i: T\n" +
           "    :rtype: tuple\n" +
           "    \"\"\"\n" +
           "    pass\n" +
           "expr = foo(5)");
  }

  public void testTupleIterationType() {
    doTest("Union[int, str]",
           "xs = (1, 'a')\n" +
           "for expr in xs:\n" +
           "    pass\n");
  }

  // PY-12801
  public void testTupleConcatenation() {
    doTest("Tuple[int, bool, str]",
           "expr = (1,) + (True, 'spam') + ()");
  }

  public void testTupleMultiplication() {
    doTest("Tuple[int, bool, int, bool]",
           "expr = (1, False) * 2");
  }


  public void testTupleDestructuring() {
    doTest("str",
           "_, expr = (1, 'val') ");
  }

  public void testParensTupleDestructuring() {
    doTest("str",
           "(_, expr) = (1, 'val') ");
  }

  // PY-19825
  public void testSubTupleDestructuring() {
    doTest("str",
           "(a, (_, expr)) = (1, (2,'val')) ");
  }

  // PY-19825
  public void testSubTupleIndirectDestructuring() {
    doTest("str",
           "xs = (2,'val')\n" +
           "(a, (_, expr)) = (1, xs) ");
  }

  public void testConstructorUnification() {
    doTest("C[int]",
           "class C(object):\n" +
           "    def __init__(self, x):\n" +
           "        '''\n" +
           "        :type x: T\n" +
           "        :rtype: C[T]\n" +
           "        '''\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10)\n");
  }

  public void testGenericClassMethodUnification() {
    doTest("int",
           "class C(object):\n" +
           "    def __init__(self, x):\n" +
           "        '''\n" +
           "        :type x: T\n" +
           "        :rtype: C[T]\n" +
           "        '''\n" +
           "        pass\n" +
           "    def foo(self):\n" +
           "        '''\n" +
           "        :rtype: T\n" +
           "        '''\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10).foo()\n");
  }

  // PY-8836
  public void testNumpyArrayIntMultiplicationType() {
    doMultiFileTest("ndarray",
                    "import numpy as np\n" +
                    "expr = np.ones(10) * 2\n");
  }

  // PY-9439
  public void testNumpyArrayType() {
    doMultiFileTest("ndarray",
                    "import numpy as np\n" +
                    "expr = np.array([1,2,3])\n");
  }

  public void testUnionTypeAttributeOfDifferentTypes() {
    doTest("Union[list, int]",
           "class Foo:\n" +
           "    x = []\n" +
           "\n" +
           "class Bar:\n" +
           "    x = 42\n" +
           "\n" +
           "def f(c):\n" +
           "    o = Foo() if c else Bar()\n" +
           "    expr = o.x\n");
  }

  // PY-11364
  public void testUnionTypeAttributeCallOfDifferentTypes() {
    doTest("Union[C1, C2]",
           "class C1:\n" +
           "    def foo(self):\n" +
           "        return self\n" +
           "\n" +
           "class C2:\n" +
           "    def foo(self):\n" +
           "        return self\n" +
           "\n" +
           "def f():\n" +
           "    '''\n" +
           "    :rtype: C1 | C2\n" +
           "    '''\n" +
           "    pass\n" +
           "\n" +
           "expr = f().foo()\n");
  }

  // PY-12862
  public void testUnionTypeAttributeSubscriptionOfDifferentTypes() {
    doTest("Union[C1, C2]",
           "class C1:\n" +
           "    def __getitem__(self, item):\n" +
           "        return self\n" +
           "\n" +
           "class C2:\n" +
           "    def __getitem__(self, item):\n" +
           "        return self\n" +
           "\n" +
           "def f():\n" +
           "    '''\n" +
           "    :rtype: C1 | C2\n" +
           "    '''\n" +
           "    pass\n" +
           "\n" +
           "expr = f()[0]\n" +
           "print(expr)\n");
  }

  // PY-11541
  public void testIsInstanceBaseStringCheck() {
    doTest("Union[str, unicode]",
           "def f(x):\n" +
           "    if isinstance(x, basestring):\n" +
           "        expr = x\n");
  }

  public void testStructuralType() {
    doTest("{foo, bar}",
           "def f(x):\n" +
           "    x.foo + x.bar()\n" +
           "    expr = x\n");
  }

  public void testOnlyRelatedNestedAttributes() {
    doTest("{foo}",
           "def g(x):\n" +
           "    x.bar\n" +
           "\n" +
           "def f(x, y):\n" +
           "    x.foo + g(y)\n" +
           "    expr = x\n");
  }

  public void testNoContainsInContainsArgumentForStructuralType() {
    doTest("{foo, __getitem__}",
           "def f(x):\n" +
           "   x in []\n" +
           "   x.foo\n" +
           "   x[0]" +
           "   expr = x\n");
  }

  public void testStructuralTypeAndIsInstanceChecks() {
    doTest("(x: {foo}) -> None",
           "def f(x):\n" +
           "    if isinstance(x, str):\n" +
           "        x.lower()\n" +
           "    x.foo\n" +
           "\n" +
           "expr = f\n");
  }

  // PY-20832
  public void testStructuralTypeWithDunderIter() {
    doTest("{__iter__}",
           "def expand(values1):\n" +
           "    for a in values1:\n" +
           "        print(a)\n" +
           "    expr = values1\n");
  }

  // PY-20833
  public void testStructuralTypeWithDunderLen() {
    doTest("{__len__}",
           "def expand(values1):\n" +
           "    a = len(values1)\n" +
           "    expr = values1\n");
  }

  // PY-16267
  public void testGenericField() {
    doTest("str",
           "class D(object):\n" +
           "    def __init__(self, foo):\n" +
           "        '''\n" +
           "        :type foo: T\n" +
           "        :rtype: D[T]\n" +
           "        '''\n" +
           "        self.foo = foo\n" +
           "\n" +
           "\n" +
           "def g():\n" +
           "    '''\n" +
           "    :rtype: D[str]\n" +
           "    '''\n" +
           "    return D('test')\n" +
           "\n" +
           "\n" +
           "y = g()\n" +
           "expr = y.foo\n");
  }

  public void testConditionInnerScope() {
    doTest("Union[str, int]",
           "if something:\n" +
           "    foo = 'foo'\n" +
           "else:\n" +
           "    foo = 0\n" +
           "\n" +
           "expr = foo\n");
  }

  public void testConditionOuterScope() {
    doTest("Union[str, int]",
           "if something:\n" +
           "    foo = 'foo'\n" +
           "else:\n" +
           "    foo = 0\n" +
           "\n" +
           "def f():\n" +
           "    expr = foo\n");
  }

  // PY-18217
  public void testConditionImportOuterScope() {
    doMultiFileTest("Union[str, int]",
                    "if something:\n" +
                    "    from m1 import foo\n" +
                    "else:\n" +
                    "    from m2 import foo\n" +
                    "\n" +
                    "def f():\n" +
                    "    expr = foo\n");
  }

  // PY-18402
  public void testConditionInImportedModule() {
    doMultiFileTest("Union[int, str]",
                    "from m1 import foo\n" +
                    "\n" +
                    "def f():\n" +
                    "    expr = foo\n");
  }

  // PY-18254
  public void testFunctionTypeCommentInStubs() {
    doMultiFileTest("MyClass",
                    "from module import func\n" +
                    "\n" +
                    "expr = func()");
  }

  // PY-19967
  public void testInheritedNamedTupleReplace() {
    PyExpression expr = parseExpr("from collections import namedtuple\n" +
                                  "class MyClass(namedtuple('T', 'a b c')):\n" +
                                  "    def get_foo(self):\n" +
                                  "        return self.a\n" +
                                  "\n" +
                                  "inst = MyClass(1,2,3)\n" +
                                  "expr = inst._replace(a=2)\n");
    doTest("MyClass",
           expr,
           TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()));
  }

  // PY-20063
  public void testIteratedSetElement() {
    doTest("int",
           "xs = {1}\n" +
           "for expr in xs:\n" +
           "    print(expr)");
  }

  public void testIsNotNone() {
    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if x is not None:\n" +
           "        expr = x\n");

    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if None is not x:\n" +
           "        expr = x\n");

    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if not x is None:\n" +
           "        expr = x\n");

    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if not None is x:\n" +
           "        expr = x\n");
  }

  public void testIsNone() {
    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if x is None:\n" +
           "        expr = x\n");

    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if None is x:\n" +
           "        expr = x\n");
  }

  public void testAnyIsNone() {
    doTest("None",
           "def test_1(c):\n" +
           "  if c is None:\n" +
           "    expr = c\n");
  }

  public void testElseAfterIsNotNone() {
    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if x is not None:\n" +
           "        print(x)\n" +
           "    else:\n" +
           "        expr = x\n");

    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if None is not x:\n" +
           "        print(x)\n" +
           "    else:\n" +
           "        expr = x\n");

    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if not x is None:\n" +
           "        print(x)\n" +
           "    else:\n" +
           "        expr = x\n");

    doTest("None",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if not None is x:\n" +
           "        print(x)\n" +
           "    else:\n" +
           "        expr = x\n");
  }

  public void testElseAfterIsNone() {
    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if x is None:\n" +
           "        print(x)\n" +
           "    else:\n" +
           "        expr = x\n");

    doTest("int",
           "def test_1(self, c):\n" +
           "    x = 1 if c else None\n" +
           "    if None is x:\n" +
           "        print(x)\n" +
           "    else:\n" +
           "        expr = x\n");
  }

  public void testElseAfterAnyIsNone() {
    doTest("Any",
           "def test_1(c):\n" +
           "  if c is None:\n" +
           "    print(c)\n" +
           "  else:\n" +
           "    expr = c\n");
  }

  // PY-21897
  public void testElseAfterIfReferenceStatement() {
    doTest("Any",
           "def test(a):\n" +
           "  if a:\n" +
           "    print(a)\n" +
           "  else:\n" +
           "    expr = a\n");
  }

  public void testHeterogeneousListLiteral() {
    doTest("List[Union[str, int]]", "expr = ['1', 1, 1]");

    doTest("List[Union[Union[str, int], Any]]", "expr = ['1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]");
  }

  public void testHeterogeneousSetLiteral() {
    doTest("Set[Union[str, int]]", "expr = {'1', 1, 1}");

    doTest("Set[Union[Union[str, int], Any]]", "expr = {'1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}");
  }

  public void testHeterogeneousDictLiteral() {
    doTest("Dict[Union[str, int], Union[str, int]]", "expr = {'1': 1, 1: '1', 1: 1}");

    doTest("Dict[Union[Union[str, int], Any], Union[Union[str, int], Any]]",
           "expr = {'1': 1, 1: '1', 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1}");
  }

  public void testHeterogeneousTupleLiteral() {
    doTest("Tuple[str, int, int]", "expr = ('1', 1, 1)");

    doTest("Tuple[str, int, int, int, int, int, int, int, int, int, int]", "expr = ('1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)");
  }

  // PY-20818
  public void testIsInstanceForSuperclass() {
    doTest("B",
           "class A:\n" +
           "    pass\n" +
           "class B(A):\n" +
           "    def foo(self):\n" +
           "        pass\n" +
           "def test():\n" +
           "    b = B()\n" +
           "    assert(isinstance(b, A))\n" +
           "    expr = b\n");
  }

  // PY-20794
  public void testIterateOverPureList() {
    doTest("Any",
           "l = None  # type: list\n" +
           "for expr in l:\n" +
           "    print(expr)\n");
  }

  // PY-20794
  public void testIterateOverDictValueWithDefaultValue() {
    doTest("Any",
           "d = None  # type: dict\n" +
           "for expr in d.get('field', []):\n" +
           "    print(expr['id'])\n");
  }

  // PY-20797
  // TODO: Enable after switching to collections stub from Typeshed
  public void _testValueOfEmptyDefaultDict() {
    doTest("list",
           "from collections import defaultdict\n" +
           "expr = defaultdict(lambda: [])['x']\n");
  }

  // PY-8473
  public void testCopyDotCopy() {
    doMultiFileTest("A",
                    "import copy\n" +
                    "class A(object):\n" +
                    "    pass\n" +
                    "expr = copy.copy(A())\n");
  }

  // PY-8473
  public void testCopyDotDeepCopy() {
    doMultiFileTest("A",
                    "import copy\n" +
                    "class A(object):\n" +
                    "    pass\n" +
                    "expr = copy.deepcopy(A())\n");
  }

  // PY-21083
  public void testFloatFromhex() {
    doTest("float",
           "expr = float.fromhex(\"0.5\")");
  }

  // PY-13159
  public void testAbsAbstractProperty() {
    doTest("str",
           "import abc\n" +
           "class D:\n" +
           "    @abc.abstractproperty\n" +
           "    def foo(self):\n" +
           "        return 'foo'\n" +
           "expr = D().foo");
  }

  public void testAbsAbstractPropertyWithFrom() {
    doTest("str",
           "from abc import abstractproperty\n" +
           "class D:\n" +
           "    @abstractproperty\n" +
           "    def foo(self):\n" +
           "        return 'foo'\n" +
           "expr = D().foo");
  }

  // TODO: enable this test when properties will be calculated with TypeEvalContext
  public void ignoredTestAbsAbstractPropertyWithAs() {
    doTest("str",
           "from abc import abstractproperty as ap\n" +
           "class D:\n" +
           "    @ap\n" +
           "    def foo(self):\n" +
           "        return 'foo'\n" +
           "expr = D().foo");
  }

  // PY-20409
  public void testGetFromDictWithDefaultNoneValue() {
    doTest("Optional[Any]",
           "d = {}\n" +
           "expr = d.get(\"abc\", None)");
  }

  // PY-20757
  public void testMinOrNone() {
    doTest("Optional[Any]",
           "def get_value(v):\n" +
           "    if v:\n" +
           "        return min(v)\n" +
           "    else:\n" +
           "        return None\n" +
           "expr = get_value([])");
  }

  // PY-21350
  public void testBuiltinInput() {
    doTest("Any",
           "expr = input()");
  }

  // PY-21350
  public void testBuiltinRawInput() {
    doTest("str",
           "expr = raw_input()");
  }

  // PY-19723
  public void testPositionalArgs() {
    doTest("Tuple[int, ...]",
           "def foo(*args):\n" +
           "    \"\"\"\n" +
           "    :type args: int\n" +
           "    \"\"\"\n" +
           "    expr = args");
  }

  // PY-19723
  public void testKeywordArgs() {
    doTest("Dict[str, int]",
           "def foo(**kwargs):\n" +
           "    \"\"\"\n" +
           "    :type kwargs: int\n" +
           "    \"\"\"\n" +
           "    expr = kwargs");
  }

  // PY-19723
  public void testIterateOverKeywordArgs() {
    doTest("str",
           "def foo(**kwargs):\n" +
           "    for expr in kwargs:\n" +
           "        pass");
  }

  // PY-19723
  public void testTypeVarSubstitutionInPositionalArgs() {
    doTest("int",
           "def foo(*args):" +
           "  \"\"\"\n" +
           "  :type args: T\n" +
           "  :rtype: T\n" +
           "  \"\"\"\n" +
           "  pass\n" +
           "expr = foo(1)");
  }

  // PY-19723
  public void testTypeVarSubstitutionInHeterogeneousPositionalArgs() {
    doTest("Union[int, str]",
           "def foo(*args):" +
           "  \"\"\"\n" +
           "  :type args: T\n" +
           "  :rtype: T\n" +
           "  \"\"\"\n" +
           "  pass\n" +
           "expr = foo(1, \"2\")");
  }

  // PY-19723
  public void testTypeVarSubstitutionInKeywordArgs() {
    doTest("int",
           "def foo(**kwargs):" +
           "  \"\"\"\n" +
           "  :type kwargs: T\n" +
           "  :rtype: T\n" +
           "  \"\"\"\n" +
           "  pass\n" +
           "expr = foo(a=1)");
  }

  // PY-19723
  public void testTypeVarSubstitutionInHeterogeneousKeywordArgs() {
    doTest("Union[int, str]",
           "def foo(**kwargs):" +
           "  \"\"\"\n" +
           "  :type kwargs: T\n" +
           "  :rtype: T\n" +
           "  \"\"\"\n" +
           "  pass\n" +
           "expr = foo(a=1, b=\"2\")");
  }

  // PY-21474
  public void testReassigningOptionalListWithDefaultValue() {
    doTest("Union[List[str], list]",
           "def x(things):\n" +
           "    \"\"\"\n" +
           "    :type things: None | list[str]\n" +
           "    \"\"\"\n" +
           "    expr = things if things else []");
  }

  public void testMinResult() {
    doTest("int",
           "expr = min(1, 2, 3)");
  }

  public void testMaxResult() {
    doTest("int",
           "expr = max(1, 2, 3)");
  }

  // PY-21692
  public void testSumResult() {
    doTest("int",
           "expr = sum([1, 2, 3])");
  }

  // PY-21994
  public void testOptionalAfterIfNot() {
    doTest("List[int]",
           "def bug(foo):\n" +
           "    \"\"\"\n" +
           "    Args:\n" +
           "        foo (list[int]|None): an optional list of ints \n" +
           "    \"\"\"\n" +
           "    if not foo:\n" +
           "        return None\n" +
           "    expr = foo");
  }

  // PY-22037
  public void testAncestorPropertyReturnsSelf() {
    doTest("Child",
           "class Master(object):\n" +
           "    @property\n" +
           "    def me(self):\n" +
           "        return self\n" +
           "class Child(Master):\n" +
           "    pass\n" +
           "child = Child()\n" +
           "expr = child.me");
  }

  // PY-22181
  public void testIterationOverIterableWithSeparateIterator() {
    doTest("int",
           "class AIter(object):\n" +
           "    def next(self):\n" +
           "        return 5\n" +
           "class A(object):\n" +
           "    def __iter__(self):\n" +
           "        return AIter()\n" +
           "a = A()\n" +
           "for expr in a:\n" +
           "    print(expr)");
  }

  public void testImportedPropertyResult() {
    doMultiFileTest("Any",
                    "from .temporary import get_class\n" +
                    "class Example:\n" +
                    "    def __init__(self):\n" +
                    "        expr = self.ins_class\n" +
                    "    @property\n" +
                    "    def ins_class(self):\n" +
                    "        return get_class()");
  }

  // PY-7322
  public void testNamedTupleParameterInDocString() {
    doTest("Point",
           "from collections import namedtuple\n" +
           "Point = namedtuple('Point', ('x', 'y'))\n" +
           "def takes_a_point(point):\n" +
           "    \"\"\"\n" +
           "    :type point: Point\n" +
           "    \"\"\"\n" +
           "    expr = point");
  }

  // PY-22919
  public void testMaxListKnownElements() {
    doTest("int",
           "expr = max([1, 2, 3])");
  }

  // PY-22919
  public void testMaxListUnknownElements() {
    doTest("Any",
           "l = []\n" +
           "expr = max(l)");
  }

  public void testWithAsType() {
    doTest("Union[A, B]",
           "from typing import Union\n" +
           "\n" +
           "class A(object):\n" +
           "    def __enter__(self):\n" +
           "        return self\n" +
           "\n" +
           "class B(object):\n" +
           "    def __enter__(self):\n" +
           "        return self\n" +
           "\n" +
           "def f(x):\n" +
           "    # type: (Union[A, B]) -> None\n" +
           "    with x as expr:\n" +
           "        pass");
  }

  // PY-23634
  public void testMinListKnownElements() {
    doTest("int",
           "expr = min([1, 2, 3])");
  }

  // PY-23634
  public void testMinListUnknownElements() {
    doTest("Any",
           "l = []\n" +
           "expr = min(l)");
  }

  // PY-21906
  public void testSOFOnTransitiveNamedTupleFields() {
    final PyExpression expression = parseExpr("from collections import namedtuple\n" +
                                              "class C:\n" +
                                              "    FIELDS = ('a', 'b')\n" +
                                              "FIELDS = C.FIELDS\n" +
                                              "expr = namedtuple('Tup', FIELDS)");

    getTypeEvalContexts(expression).forEach(context -> context.getType(expression));
  }

  // PY-22971
  public void testFirstOverloadAndImplementationInClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("int",
                   "from typing import overload\n" +
                   "class A:\n" +
                   "    @overload\n" +
                   "    def foo(self, value: int) -> int:\n" +
                   "        pass\n" +
                   "    @overload\n" +
                   "    def foo(self, value: str) -> str:\n" +
                   "        pass\n" +
                   "    def foo(self, value):\n" +
                   "        return None\n" +
                   "expr = A().foo(5)")
    );
  }

  // PY-22971
  public void testTopLevelFirstOverloadAndImplementation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("int",
                   "from typing import overload\n" +
                   "@overload\n" +
                   "def foo(value: int) -> int:\n" +
                   "    pass\n" +
                   "@overload\n" +
                   "def foo(value: str) -> str:\n" +
                   "    pass\n" +
                   "def foo(value):\n" +
                   "    return None\n" +
                   "expr = foo(5)")
    );
  }

  // PY-22971
  public void testFirstOverloadAndImplementationInImportedClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doMultiFileTest("int",
                            "from b import A\n" +
                            "expr = A().foo(5)")
    );
  }

  // PY-22971
  public void testFirstOverloadAndImplementationInImportedModule() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doMultiFileTest("int",
                            "from b import foo\n" +
                            "expr = foo(5)")
    );
  }

  // PY-22971
  public void testSecondOverloadAndImplementationInClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("str",
                   "from typing import overload\n" +
                   "class A:\n" +
                   "    @overload\n" +
                   "    def foo(self, value: int) -> int:\n" +
                   "        pass\n" +
                   "    @overload\n" +
                   "    def foo(self, value: str) -> str:\n" +
                   "        pass\n" +
                   "    def foo(self, value):\n" +
                   "        return None\n" +
                   "expr = A().foo(\"5\")")
    );
  }

  // PY-22971
  public void testTopLevelSecondOverloadAndImplementation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("str",
                   "from typing import overload\n" +
                   "@overload\n" +
                   "def foo(value: int) -> int:\n" +
                   "    pass\n" +
                   "@overload\n" +
                   "def foo(value: str) -> str:\n" +
                   "    pass\n" +
                   "def foo(value):\n" +
                   "    return None\n" +
                   "expr = foo(\"5\")")
    );
  }

  // PY-22971
  public void testSecondOverloadAndImplementationInImportedClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doMultiFileTest("str",
                            "from b import A\n" +
                            "expr = A().foo(\"5\")")
    );
  }

  // PY-22971
  public void testSecondOverloadAndImplementationInImportedModule() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doMultiFileTest("str",
                            "from b import foo\n" +
                            "expr = foo(\"5\")")
    );
  }

  // PY-22971
  public void testNotMatchedOverloadsAndImplementationInClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Union[int, str]",
                   "from typing import overload\n" +
                   "class A:\n" +
                   "    @overload\n" +
                   "    def foo(self, value: int) -> int:\n" +
                   "        pass\n" +
                   "    @overload\n" +
                   "    def foo(self, value: str) -> str:\n" +
                   "        pass\n" +
                   "    def foo(self, value):\n" +
                   "        return None\n" +
                   "expr = A().foo(object())")
    );
  }

  // PY-22971
  public void testTopLevelNotMatchedOverloadsAndImplementation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Union[int, str]",
                   "from typing import overload\n" +
                   "@overload\n" +
                   "def foo(value: int) -> int:\n" +
                   "    pass\n" +
                   "@overload\n" +
                   "def foo(value: str) -> str:\n" +
                   "    pass\n" +
                   "def foo(value):\n" +
                   "    return None\n" +
                   "expr = foo(object())")
    );
  }

  // PY-22971
  public void testNotMatchedOverloadsAndImplementationInImportedClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doMultiFileTest("Union[int, str]",
                            "from b import A\n" +
                            "expr = A().foo(object())")
    );
  }

  // PY-22971
  public void testNotMatchedOverloadsAndImplementationInImportedModule() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doMultiFileTest("Union[int, str]",
                            "from b import foo\n" +
                            "expr = foo(object())")
    );
  }

  // PY-24383
  public void testSubscriptionOnWeakType() {
    doTest("Union[int, Any]",
           "foo = bar() if 42 != 42 else [1, 2, 3, 4]\n" +
           "expr = foo[0]");
  }

  // PY-24364
  public void testReassignedParameter() {
    doTest("(entries: Any) -> Generator[Any, Any, None]",
           "def resort(entries):\n" +
           "    entries = list(entries)\n" +
           "    entries.sort(reverse=True)\n" +
           "    for entry in entries:\n" +
           "        yield entry\n" +
           "expr = resort");
  }

  public void testIsSubclass() {
    doTest("Type[A]",
           "class A: pass\n" +
           "def foo(cls):\n" +
           "    if issubclass(cls, A):\n" +
           "        expr = cls");
  }

  public void testIsSubclassWithTupleOfTypeObjects() {
    doTest("Type[Union[A, B]]",
           "class A: pass\n" +
           "class B: pass\n" +
           "def foo(cls):\n" +
           "    if issubclass(cls, (A, B)):\n" +
           "        expr = cls");
  }

  // PY-24323
  public void testMethodQualifiedWithUnknownGenericsInstance() {
    doTest("(object: Any) -> int",
           "my_list = []\n" +
           "expr = my_list.count");
  }

  // PY-24323
  public void testMethodQualifiedWithKnownGenericsInstance() {
    doTest("(object: int) -> int",
           "my_list = [1, 2, 2, 3, 3]\n" +
           "expr = my_list.count");
  }

  public void testConstructingGenericClassWithNotFilledGenericValue() {
    doTest("MyIterator",
           "from typing import Iterator\n" +
           "class MyIterator(Iterator[]):\n" +
           "    def __init__(self) -> None:\n" +
           "        self.other = \"other\"\n" +
           "expr = MyIterator()");
  }

  // PY-24923
  public void testEmptyNumpyFunctionDocstring() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () ->
      doTest("Any",
             "def f(param):\n" +
             "    \"\"\"\"\"\"\n" +
             "    expr = param"));
  }

  // PY-24923
  public void testEmptyNumpyClassDocstring() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () ->
      doTest("Any",
             "class C:\n" +
             "    \"\"\"\"\"\"\n" +
             "    def __init__(self, param):\n" +
             "        expr = param"));
  }

  // PY-21175
  public void testNoneTypeFilteredOutByConditionalAssignment() {
    doTest("List[int]",
           "xs = None\n" +
           "if xs is None:\n" +
           "    xs = [1, 2, 3]\n" +
           "expr = xs\n");
  }

  // PY-21175
  public void testAnyAddedByConditionalDefinition() {
    doTest("Union[str, Any]",
           "def f(x, y):\n" +
           "    if x:\n" +
           "        var = y\n" +
           "    else:\n" +
           "        var = 'foo'\n" +
           "    expr = var");
  }

  // PY-21626
  public void testNestedConflictingIsNoneChecksInitialAny() {
    doTest("Optional[Any]",
           "def f(x):\n" +
           "    if x is None:\n" +
           "        if x is not None:\n" +
           "            pass\n" +
           "    expr = x");
  }

  // PY-21626
  public void testNestedConflictingIsNoneChecksInitialKnown() {
    doTest("Optional[str]",
           "x = 'foo'\n" +
           "if x is None:\n" +
           "    if x is not None:\n" +
           "        pass\n" +
           "expr = x");
  }

  // PY-21175
  public void testLazyAttributeInitialization() {
    doTest("int",
           "class C:\n" +
           "    def __init__(self):\n" +
           "        self.attr = None\n" +
           "    \n" +
           "    def m(self):\n" +
           "        if self.attr is None:\n" +
           "            self.attr = 42\n" +
           "        expr = self.attr");
  }

  // PY-21175
  public void testAssignmentToAttributeOfCallResultWithNameOfLocalVariable() {
    doTest("int",
           "def f(g):\n" +
           "    x = 42\n" +
           "    if True:\n" +
           "        g().x = 'foo'\n" +
           "    expr = x");
  }

  public void testTypingNTInheritor() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("from typing import NamedTuple\n" +
                                                  "class User(NamedTuple):\n" +
                                                  "    name: str\n" +
                                                  "    level: int = 0\n" +
                                                  "expr = User");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          final PyType type = context.getType(definition);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyClassType.class);
        }

        final PyExpression instance = parseExpr("from typing import NamedTuple\n" +
                                                "class User(NamedTuple):\n" +
                                                "    name: str\n" +
                                                "    level: int = 0\n" +
                                                "expr = User(\"name\")");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          final PyType type = context.getType(instance);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyClassType.class);
        }
      }
    );
  }

  public void testTypingNTTarget() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("from typing import NamedTuple\n" +
                                                  "User = NamedTuple(\"User\", name=str, level=int)\n" +
                                                  "expr = User");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("from typing import NamedTuple\n" +
                                                "User = NamedTuple(\"User\", name=str, level=int)\n" +
                                                "expr = User(\"name\")");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          assertInstanceOf(context.getType(instance), PyNamedTupleType.class);
        }
      }
    );
  }

  public void testCollectionsNTInheritor() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("from collections import namedtuple\n" +
                                                  "class User(namedtuple(\"User\", \"name level\")):\n" +
                                                  "    pass\n" +
                                                  "expr = User");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          final PyType type = context.getType(definition);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("from collections import namedtuple\n" +
                                                "class User(namedtuple(\"User\", \"name level\")):\n" +
                                                "    pass\n" +
                                                "expr = User('MrRobot')");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          final PyType type = context.getType(instance);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyNamedTupleType.class);
        }
      }
    );
  }

  public void testCollectionsNTTarget() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("from collections import namedtuple\n" +
                                                  "User = namedtuple(\"User\", \"name level\")\n" +
                                                  "expr = User");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("from collections import namedtuple\n" +
                                                "User = namedtuple(\"User\", \"name level\")\n" +
                                                "expr = User('MrRobot')");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          assertInstanceOf(context.getType(instance), PyNamedTupleType.class);
        }
      }
    );
  }

  // PY-25157
  public void testFunctionWithDifferentNamedTuplesAsParameterAndReturnTypes() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("(a: MyType1) -> MyType2",
                   "from collections import namedtuple\n" +
                   "MyType1 = namedtuple('MyType1', 'x y')\n" +
                   "MyType2 = namedtuple('MyType2', 'x y')\n" +
                   "def foo(a: MyType1) -> MyType2:\n" +
                   "    pass\n" +
                   "expr = foo")
    );
  }

  // PY-25346
  public void testTypingNTInheritorField() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("int",
                   "from typing import NamedTuple\n" +
                   "class User(NamedTuple):\n" +
                   "    name: str\n" +
                   "    level: int = 0\n" +
                   "expr = User(\"name\").level")
    );
  }

  // PY-25346
  public void testTypingNTTargetField() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("int",
                   "from typing import NamedTuple\n" +
                   "User = NamedTuple(\"User\", name=str, level=int)\n" +
                   "expr = User(\"name\").level")
    );
  }

  // PY-18791
  public void testCallOnProperty() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Iterator[int]",
                   "from typing import Iterator, Callable\n" +
                   "class Foo:\n" +
                   "    def iterate(self) -> Iterator[int]:\n" +
                   "        pass\n" +
                   "    @property\n" +
                   "    def foo(self) -> Callable[[], Iterator[int]]:\n" +
                   "        return self.iterate\n" +
                   "expr = Foo().foo()")
    );
  }

  // PY-9662
  public void testBinaryExpressionWithUnknownOperand() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        doTest("Union[int, Any]",
               "from typing import Any\n" +
               "x: Any\n" +
               "expr = x * 2");

        doTest("Union[int, Any]",
               "from typing import Any\n" +
               "x: Any\n" +
               "expr = 2 * x");

        doTest("Union[int, Any]",
               "def f(x):\n" +
               "    expr = x * 2");

        doTest("Union[int, Any]",
               "def f(x):\n" +
               "    expr = 2 * x");
      }
    );
  }

  // PY-24960
  public void testOperatorReturnsAny() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Union[bool, Any]",
                   "from typing import Any\n" +
                   "class Bar:\n" +
                   "    def __eq__(self, other) -> Any:\n" +
                   "        pass\n" +
                   "expr = (Bar() == 2)")
    );
  }

  // PY-24240
  public void testImplicitSuper() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON30,
      () -> {
        final PyExpression expression = parseExpr("class A:\n" +
                                                  "    pass\n" +
                                                  "expr = A");

        for (TypeEvalContext context : getTypeEvalContexts(expression)) {
          final PyType type = context.getType(expression);
          assertInstanceOf(type, PyClassType.class);

          final PyClassType objectType = PyBuiltinCache.getInstance(expression).getObjectType();
          assertNotNull(objectType);

          assertEquals(Collections.singletonList(objectType.toClass()), ((PyClassType)type).getSuperClassTypes(context));
        }
      }
    );
  }

  // PY-25545
  public void testDunderInitSubclassFirstParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Type[Foo]",
                   "class Foo:\n" +
                   "    def __init_subclass__(cls):\n" +
                   "        expr = cls")
    );
  }

  private static List<TypeEvalContext> getTypeEvalContexts(@NotNull PyExpression element) {
    return ImmutableList.of(TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()).withTracing(),
                            TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()).withTracing());
  }

  @Nullable
  private PyExpression parseExpr(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    return myFixture.findElementByText("expr", PyExpression.class);
  }

  private static void doTest(final String expectedType, final PyExpression expr, final TypeEvalContext context) {
    assertType(expectedType, expr, context);
  }

  private void doTest(@NotNull final String expectedType, @NotNull final String text) {
    checkTypes(expectedType, parseExpr(text));
  }

  private static void checkTypes(@NotNull String expectedType, @Nullable PyExpression expr) {
    assertNotNull(expr);
    for (TypeEvalContext context : getTypeEvalContexts(expr)) {
      assertType(expectedType, expr, context);
    }
  }

  public static final String TEST_DIRECTORY = "/types/";

  private void doMultiFileTest(@NotNull  final String expectedType, @NotNull final String text) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    checkTypes(expectedType, parseExpr(text));
  }
}
