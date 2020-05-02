// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.google.common.collect.ImmutableList;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.*;
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
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("str",
                   "def foo(x: str) -> list:\n" +
                   "    expr = x")
    );
  }

  public void testReturnTypeAnno() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("list",
                   "def foo(x) -> list:\n" +
                   "    return x\n" +
                   "expr = foo(None)")
    );
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
    doTest("int", "def f():\n" +
                  "    '''\n" +
                  "    :rtype: int or slice\n" +
                  "    '''\n" +
                  "    raise NotImplementedError\n" +
                  "\n" +
                  "x = f()\n" +
                  "expr = x.bit_length()\n");
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
    doTest("BinaryIO",
           "expr = open('foo')\n");
  }

  public void testOpenText() {
    doTest("BinaryIO",
           "expr = open('foo', 'r')\n");
  }

  public void testOpenBinary() {
    doTest("BinaryIO",
           "expr = open('foo', 'rb')\n");
  }

  public void testIoOpenDefault() {
    doTest("TextIO",
           "import io\n" +
           "expr = io.open('foo')\n");
  }

  public void testIoOpenText() {
    doTest("TextIO",
           "import io\n" +
           "expr = io.open('foo', 'r')\n");
  }

  public void testIoOpenBinary() {
    doTest("BinaryIO",
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
      () -> doTest("Tuple[int, ...]",
                   "from typing import TypeVar, Tuple\n" +
                   "T = TypeVar('T')\n" +
                   "def foo(i: T) -> Tuple[T, ...]:\n" +
                   "    pass\n" +
                   "expr = foo(5)")
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

  // PY-1182
  public void testListTypeByModifications() {
    doTest("List[int]",
           "def f():\n" +
           "    expr = []\n" +
           "    expr.append(42)\n" +
           "    expr.append(0)"
    );

    doTest("List[Union[str, int]]",
           "def f():\n" +
           "    expr = []\n" +
           "    expr.append('a')\n" +
           "    expr.append(1)"
    );

    doTest("List[Union[Union[int, str], Any]]",
           "expr = [3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4]\n" +
           "expr.append('a')"
    );

    doTest("List[Union[int, str]]",
           "expr = [1, 2]\n" +
           "expr.append(42)\n" +
           "expr.extend(['a']"
    );

    doTest("List[Union[int, str, None]]",
           "expr = []\n" +
           "expr.extend([1, 'a', None])"
    );

    doTest("List[int]",
           "expr = []\n" +
           "expr.index(42)");

    doTest("List[int]",
           "expr = [1, 2, 3]\n");

    doTest("List[Union[int, Any]]",
           "expr = [1, 2, 3]\n" +
           "expr.append(var)\n");

    doTest("List[Union[int, str]]",
           "expr = [1, 2, 3]\n" +
           "expr[0] = 'a'\n" +
           "expr[1] = 'b'\n");

    doTest("List[Union[int, str]]",
           "expr = [1, 2, 3]\n" +
           "expr[0] = 'a'\n" +
           "expr[1] = 'b'\n");

    doTest("List[Union[int, str]]",
           "expr = [1, 2]\n" +
           "t, expr[1] = 23, 'b'\n");

    doTest("List[Union[int, str]]",
           "def f():\n" +
           "    expr, b = [1, 2, 3], 'abc'\n" +
           "    expr.append('a')\n"
    );

    doTest("List[int]",
           "def f():" +
           "    expr = [1, 2, 3]\n" +
           "    def inner():\n" +
           "        expr.append('a')\n"
    );
  }

  // PY-1182
  public void testListTypeByModificationsConstructor() {
    doTest("List[str]",
           "expr = list()\n" +
           "expr.append('a')\n"
    );

    doTest("List[Union[str, int]]",
           "expr = list()\n" +
           "expr.append('a')\n" +
           "expr.append(1)\n"
    );

    doTest("List[Union[int, str]]",
           "a = list([1, 2, 3])\n" +
           "a.append('a')\n" +
           "expr = a\n"
    );

    doTest("List[Union[str, Any]]",
           "expr = list()\n" +
           "expr.append('a')\n" +
           "expr.append(var)\n"
    );

    doTest("List[Union[int, str]]",
           "expr = list([1, 2])\n" +
           "t, expr[1] = 23, 'b'\n");

    doTest("List[Union[str, Any]]",
           "expr = list(var)\n" +
           "expr[0] = 'abc'\n");

    doTest("List[Union[int, str]]",
           "b, expr = 1, list([1, 2, 3])\n" +
           "expr.append('a')\n"
    );

    doTest("List[int]",
           "def f():" +
           "    expr = list([1, 2, 3])\n" +
           "    def inner():\n" +
           "        expr.append('a')\n"
    );
  }

  // PY-29577
  public void testRangeTypeByModifications() {
    doTest("List[int]",
           "expr = range(10)\n");

    doTest("List[Union[int, str]]",
           "expr = range(10)\n" +
           "expr.append('a')");

    doTest("List[Union[int, Any]]",
           "expr = range(10)\n" +
           "expr.append(var)\n");

    doTest("List[Union[int, str]]",
           "expr = range(10)\n" +
           "expr[0] = 'a'\n");

    doTest("List[Union[int, str, None]]",
           "expr = range(10)\n" +
           "expr.extend(['a', None])");

    doTest("List[Union[int, str]]",
           "expr = range(10)\n" +
           "expr.index('a')");
  }

  // PY-1182
  public void testDictTypeByModifications() {
    doTest("Dict[str, Union[int, str]]",
           "def f():\n" +
           "    expr = {'a': 3}\n" +
           "    expr['b'] = \"s\""
    );

    doTest("Dict[str, Union[int, str]]",
           "def f():\n" +
           "    expr = {'a': 3}\n" +
           "    expr['b'] = \"s\""
    );

    doTest("Dict[str, Union[int, List[int]]]",
           "def f():\n" +
           "    expr = {}\n" +
           "    expr['a'] = 0\n" +
           "    expr['c'] = [1, 2]"
    );

    doTest("Dict[str, Union[int, Any]]",
           "def f():\n" +
           "    expr = {'b': D()}\n" +
           "    expr['a'] = 2\n"
    );

    doTest("Dict[str, Union[int, str]]",
           "def f():\n" +
           "    expr = {'a': 3}\n" +
           "    expr['b'], t = \"s\", 12"
    );

    doTest("Dict[str, Union[int, Any]]",
           "def f():\n" +
           "    expr = {'a': 3}\n" +
           "    expr['a'] = var\n"
    );

    doTest("Dict[str, int]",
           "def f():\n" +
           "    expr = {'a': 3, 'b': 4}\n"
    );

    doTest("Dict[str, Union[int, str]]",
           "def f():\n" +
           "    expr = {'a': 3}\n" +
           "    expr.update({'a': 'str'})\n"
    );

    doTest("Dict[str, Union[int, Any]]",
           "def f():\n" +
           "    expr = {'a': 3}\n" +
           "    expr.update({'b': var})\n"
    );

    doTest("Dict[str, int]",
           "def f():\n" +
           "    expr = {}\n" +
           "    expr.update(a=1, b=2)"
    );

    doTest("Dict[Union[int, str], Union[int, str]]",
           "def f():\n" +
           "    expr = {1: '3'}\n" +
           "    expr.update(a=1, b=2)"
    );

    doTest("Dict[str, Union[int, str]]",
           "def f():\n" +
           "    expr = {}\n" +
           "    expr['a'] = 23\n" +
           "    expr.update(a='m', b='n')"
    );

    doTest("Dict[str, Union[int, str]]",
           "def f():\n" +
           "    b, expr = 23, {'a': 3}\n" +
           "    expr['b'] = 'l'"
    );

    doTest("Dict[str, int]",
           "def f():" +
           "    expr = {'a': 1}\n" +
           "    def inner():\n" +
           "        expr['b'] = 'a'\n"
    );
  }

  // PY-1182
  public void testDictTypeByModificationConstructor() {
    doTest("Dict[str, int]",
           "expr = dict()\n" +
           "expr['d'] = 12\n"
    );

    doTest("Dict[str, Union[int, str]]",
           "expr = dict({'a': 1, 'b': 2})\n" +
           "expr['a'] = '12'\n"
    );

    doTest("Dict[str, Union[int, str]]",
           "expr = dict(zip(['a', 'b', 'c'], [1, 2, 3]))\n" +
           "expr['d'] = '12'\n"
    );

    doTest("Dict[str, Union[int, str]]",
           "expr = dict(zip(['a', 'b', 'c'], [1, 2, 3]))\n" +
           "expr['d'] = '12'\n"
    );

    doTest("Dict[str, Union[int, str]]",
           "expr = dict([('two', 2), ('one', 1), ('three', 3)])\n" +
           "expr['d'] = '12'\n"
    );

    doTest("Dict[str, Union[int, Any]]",
           "expr = dict({'a': 1, 'b': 2})\n" +
           "expr['a'] = var\n"
    );

    doTest("Dict[Union[str, Any], Union[int, Any]]",
           "expr = dict(var)\n" +
           "expr.update({'c': 12})\n"
    );

    doTest("Dict[str, Union[int, str]]",
           "a, expr = 23, dict({'a': 1})\n" +
           "expr.update({'c': '34'})\n"
    );

    doTest("Dict[str, int]",
           "def f():" +
           "    expr = dict({'a': 1})\n" +
           "    def inner():\n" +
           "        expr['b'] = 'a'\n"
    );
  }

  // PY-1182
  public void testSetTypeByModifications() {
    doTest("Set[Union[str, int]]",
           "def f():\n" +
           "    expr = {'abc'}\n" +
           "    expr.add(1)"
    );

    doTest("Set[Union[int, str]]",
           "def f():\n" +
           "    expr = {1, 2}\n" +
           "    b = {'abc'}\n" +
           "    expr.update(b)"
    );

    doTest("Set[Union[int, str]]",
           "def f():\n" +
           "    expr = {1, 2}\n" +
           "    b = {2, 3}\n" +
           "    expr.update(b, ['a', 'b'], {1, 2})"
    );

    doTest("Set[str]",
           "def f():\n" +
           "    expr = {'m', 'n'}\n" +
           "    expr.update({'a': 1, 'b': 2})"
    );

    doTest("Set[Union[Union[int, str], Any]]",
           "def f():\n" +
           "    expr = {1, 2}\n" +
           "    b = {'a', 'b'}\n" +
           "    expr.update(b, var)"
    );

    doTest("Set[str]",
           "def f():\n" +
           "    expr, var = {'a', 'b'}, 'lala'\n" +
           "    expr.add('b')"
    );

    doTest("Set[int]",
           "def f():" +
           "    expr = {1, 2, 3}\n" +
           "    def inner():\n" +
           "        expr.add('a')\n"
    );
  }

  // PY-1182
  public void testSetTypeByModificationsConstructor() {
    doTest("Set[int]",
           "def f():\n" +
           "    expr = set()\n" +
           "    expr.add(1)"
    );

    doTest("Set[Union[int, str]]",
           "def f():\n" +
           "    expr = set({1, 2})\n" +
           "    expr.add('abc')"
    );

    doTest("Set[Union[int, str]]",
           "def f():\n" +
           "    expr = set({1, 2})\n" +
           "    b = {'abc'}\n" +
           "    expr.update(b)"
    );

    doTest("Set[Union[int, str]]",
           "def f():\n" +
           "    expr = set({1, 2})\n" +
           "    b = {2, 3}\n" +
           "    expr.update(b, ['a', 'b'], {1, 2})"
    );

    doTest("Set[Union[str, Any]]",
           "def f():\n" +
           "    expr = set()\n" +
           "    b = {'a', 'b'}\n" +
           "    expr.update(b, var)"
    );

    doTest("Set[Union[str, Any]]",
           "def f():\n" +
           "    expr = set(var)\n" +
           "    b = {'a', 'b'}\n" +
           "    expr.update(b)"
    );

    doTest("Set[Union[int, str]]",
           "def f():\n" +
           "    expr, var = set([1, 2, 3]), 'lala'\n" +
           "    expr.add('b')"
    );

    doTest("Set[int]",
           "def f():\n" +
           "    expr = set()\n" +
           "    expr.add(1)\n" +
           "    def inner():\n" +
           "        expr.add('a')\n"
    );
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
  public void testValueOfEmptyDefaultDict() {
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

  // PY-37755
  public void testGlobalType() {
    doTest("list",
           "expr = []\n" +
           "\n" +
           "def fun():\n" +
           "    global expr\n" +
           "    expr");

    doTest("list",
           "expr = []\n" +
           "\n" +
           "def fun():\n" +
           "    def nuf():\n" +
           "        global expr\n" +
           "        expr");

    doTest("list",
           "expr = []\n" +
           "\n" +
           "def fun():\n" +
           "    expr = True\n" +
           "    \n" +
           "    def nuf():\n" +
           "        global expr\n" +
           "        expr");

    doTest("Union[bool, int]",
           "if True:\n" +
           "    a = True\n" +
           "else:\n" +
           "    a = 5\n" +
           "\n" +
           "def fun():\n" +
           "    def nuf():\n" +
           "        global a\n" +
           "        expr = a");
  }

  // PY-37755
  public void testNonLocalType() {
    doTest("bool",
           "def fun():\n" +
           "    expr = True\n" +
           "\n" +
           "    def nuf():\n" +
           "        nonlocal expr\n" +
           "        expr");

    doTest("bool",
           "a = []\n" +
           "\n" +
           "def fun():\n" +
           "    a = True\n" +
           "\n" +
           "    def nuf():\n" +
           "        nonlocal a\n" +
           "        expr = a");

    doTest("Union[bool, int]",
           "a = []\n" +
           "\n" +
           "def fun():\n" +
           "    if True:\n" +
           "        a = True\n" +
           "    else:\n" +
           "        a = 5\n" +
           "\n" +
           "    def nuf():\n" +
           "        nonlocal a\n" +
           "        expr = a");
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
    doTest("(__value: Any) -> int",
           "my_list = []\n" +
           "expr = my_list.count");
  }

  // PY-24323
  public void testMethodQualifiedWithKnownGenericsInstance() {
    doTest("(__value: int) -> int",
           "my_list = [1, 2, 2, 3, 3]\n" +
           "expr = my_list.count");
  }

  // PY-26616
  public void testClassMethodQualifiedWithDefinition() {
    doTest("(x: str) -> Foo",
           "class Foo:\n" +
           "    @classmethod\n" +
           "    def make_foo(cls, x: str) -> 'Foo':\n" +
           "        pass\n" +
           "expr = Foo.make_foo");
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

  // PY-32240
  public void testTypingNTFunctionInheritorField() {
    doTest("str",
           "from typing import NamedTuple\n" +
           "\n" +
           "class A(NamedTuple(\"NT\", [(\"user\", str)])):\n" +
           "    pass\n" +
           "    \n" +
           "expr = A(undefined).user");
  }

  // PY-4351
  public void testCollectionsNTInheritorField() {
    // Seems that this case won't be supported because
    // it requires to update ancestor, not class itself, for every `User(...)` call
    doTest("Any",
           "from collections import namedtuple\n" +
           "class User(namedtuple(\"User\", \"name age\")):\n" +
           "    pass\n" +
           "expr = User(\"name\", 13).age");
  }

  // PY-4351
  public void testCollectionsNTTargetField() {
    doTest("int",
           "from collections import namedtuple\n" +
           "User = namedtuple(\"User\", \"name age\")\n" +
           "expr = User(\"name\", 13).age");
  }

  // PY-4351
  public void testTypingNTInheritorUnpacking() {
    doTest("int",
           "from typing import NamedTuple\n" +
           "class User(NamedTuple(\"User\", [(\"name\", str), (\"age\", int)])):\n" +
           "    pass\n" +
           "y2, expr = User(\"name\", 13)");
  }

  // PY-4351
  public void testTypingNTTargetUnpacking() {
    doTest("int",
           "from typing import NamedTuple\n" +
           "Point2 = NamedTuple('Point', [('x', int), ('y', str)])\n" +
           "p2 = Point2(1, \"1\")\n" +
           "expr, y2 = p2");
  }

  // PY-4351
  public void testCollectionsNTInheritorUnpacking() {
    // Seems that this case won't be supported because
    // it requires to update ancestor, not class itself, for every `User(...)` call
    doTest("Any",
           "from collections import namedtuple\n" +
           "class User(namedtuple(\"User\", \"name ags\")):\n" +
           "    pass\n" +
           "y1, expr = User(\"name\", 13)");
  }

  // PY-4351
  public void testCollectionsNTTargetUnpacking() {
    doTest("int",
           "from collections import namedtuple\n" +
           "Point = namedtuple('Point', ['x', 'y'])\n" +
           "p1 = Point(1, '1')\n" +
           "expr, y1 = p1");
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
      LanguageLevel.PYTHON34,
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

  // PY-27913
  public void testDunderClassGetItemFirstParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> doTest("Type[Foo]",
                   "class Foo:\n" +
                   "    def __class_getitem__(cls, item):\n" +
                   "        expr = cls")
    );
  }

  public void testNoneLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("None",
                   "expr = None")
    );
  }

  public void testEllipsis() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("Any",
                   "expr = ...")
    );
  }

  // PY-25751
  public void testNotImportedModuleInDunderAll() {
    doMultiFileTest("Union[aaa.py, Any]",
                    "from pkg import *\n" +
                    "expr = aaa");
  }

  // PY-25751
  public void testNotImportedPackageInDunderAll() {
    doMultiFileTest("Union[__init__.py, Any]",
                    "from pkg import *\n" +
                    "expr = aaa");
  }

  // PY-26269
  public void testDontReplaceDictValueWithReceiverType() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Dict[str, Any]",
                   "from typing import Any, Dict\n" +
                   "d: Dict[str, Dict[str, Any]]\n" +
                   "expr = d[\"k\"]")
    );
  }

  // PY-26493
  public void testAssertAndStructuralType() {
    doTest("str",
           "def run_workloads(cfg):\n" +
           "    assert isinstance(cfg, str)\n" +
           "    cfg.split()\n" +
           "    expr = cfg");
  }

  // PY-26061
  public void testUnknownDictValues() {
    doTest("list",
           "expr = dict().values()");
  }

  // PY-26061
  public void testUnresolvedGenericReplacement() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Any",
                   "from typing import TypeVar, Generic, List\n" +
                   "\n" +
                   "T = TypeVar('T')\n" +
                   "V = TypeVar('V')\n" +
                   "\n" +
                   "class B(Generic[T]):\n" +
                   "    def f(self) -> T:\n" +
                   "        ...\n" +
                   "\n" +
                   "class C(B[V], Generic[V]):\n" +
                   "    pass\n" +
                   "\n" +
                   "expr = C().f()\n")
    );
  }

  // PY-26643
  public void testReplaceSelfInGenerator() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("Generator[B, Any, B]",
                   "class A:\n" +
                   "    def foo(self):\n" +
                   "        yield self\n" +
                   "        return self\n" +
                   "class B(A):\n" +
                   "    pass\n" +
                   "expr = B().foo()")
    );
  }

  public void testReplaceSelfInUnion() {
    doTest("Union[B, int]",
           "class A:\n" +
           "    def foo(self, x):\n" +
           "        if x:\n" +
           "            return self\n" +
           "        else:\n" +
           "            return 1\n" +
           "class B(A):\n" +
           "    pass\n" +
           "expr = B().foo(abc)");
  }

  // PY-27143
  public void testReplaceInstanceInClassMethod() {
    doTest("Derived",
           "class Base:\n" +
           "    @classmethod\n" +
           "    def instance(cls):\n" +
           "        return cls()\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived.instance()");

    doTest("Derived",
           "class Base:\n" +
           "    @classmethod\n" +
           "    def instance(cls):\n" +
           "        return cls()\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived().instance()");
  }

  // PY-27143
  public void testReplaceInstanceInMethod() {
    doTest("Derived",
           "class Base:\n" +
           "    def instance(self):\n" +
           "        return self\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived.instance(Derived())");

    doTest("Derived",
           "class Base:\n" +
           "    def instance(self):\n" +
           "        return self\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived().instance()");
  }

  // PY-27143
  public void testReplaceDefinitionInClassMethod() {
    doTest("Type[Derived]",
           "class Base:\n" +
           "    @classmethod\n" +
           "    def cls(cls):\n" +
           "        return cls\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived.cls()");

    doTest("Type[Derived]",
           "class Base:\n" +
           "    @classmethod\n" +
           "    def cls(cls):\n" +
           "        return cls\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived().cls()");
  }

  // PY-27143
  public void testReplaceDefinitionInMethod() {
    doTest("Type[Derived]",
           "class Base:\n" +
           "    def cls(self):\n" +
           "        return self.__class__\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived.cls(Derived())");

    doTest("Type[Derived]",
           "class Base:\n" +
           "    def cls(self):\n" +
           "        return self.__class__\n" +
           "class Derived(Base):\n" +
           "    pass\n" +
           "expr = Derived().cls()");
  }

  // PY-26992
  public void testInitializingInnerCallableClass() {
    doTest("B",
           "class A:\n" +
           "    class B:\n" +
           "        def __init__(self):\n" +
           "            pass\n" +
           "        def __call__(self, x):\n" +
           "            pass\n" +
           "    def __init__(self):\n" +
           "        pass\n" +
           "expr = A.B()");
  }

  // PY-26992
  public void testInitializingInnerCallableClassThroughExplicitDunderInit() {
    doTest("B",
           "class A:\n" +
           "    class B:\n" +
           "        def __init__(self):\n" +
           "            pass\n" +
           "        def __call__(self, x):\n" +
           "            pass\n" +
           "    def __init__(self):\n" +
           "        pass\n" +
           "expr = A.B.__init__()");
  }

  // PY-26992
  public void testInitializingInnerCallableClassThroughExplicitDunderNew() {
    doTest("B",
           "class A(object):\n" +
           "    class B(object):\n" +
           "        def __init__(self):\n" +
           "            pass\n" +
           "        def __call__(self, x):\n" +
           "            pass\n" +
           "    def __init__(self):\n" +
           "        pass\n" +
           "expr = A.B.__new__(A.B)");
  }

  // PY-26973
  public void testSliceOnUnion() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Union[str, Any]",
                   "from typing import Union\n" +
                   "myvar: Union[str, int]\n" +
                   "expr = myvar[0:3]")
    );
  }

  // PY-22945
  public void testNotInstalledTypingUsedInAnalysis() {
    doTest("Pattern[str]",
                    "from re import compile\n" +
                    "expr = compile(\"str\")");
  }

  // PY-27148
  public void testCollectionsNTMake() {
    doTest("Cat",
           "from collections import namedtuple\n" +
           "Cat = namedtuple(\"Cat\", \"name age\")\n" +
           "expr = Cat(\"name\", 5)._make([\"newname\", 6])");

    doTest("Cat",
           "from collections import namedtuple\n" +
           "Cat = namedtuple(\"Cat\", \"name age\")\n" +
           "expr = Cat._make([\"newname\", 6])");

    doTest("Cat",
           "from collections import namedtuple\n" +
           "class Cat(namedtuple(\"Cat\", \"name age\")):\n" +
           "    pass\n" +
           "expr = Cat(\"name\", 5)._make([\"newname\", 6])");

    doTest("Cat",
           "from collections import namedtuple\n" +
           "class Cat(namedtuple(\"Cat\", \"name age\")):\n" +
           "    pass\n" +
           "expr = Cat._make([\"newname\", 6])");
  }

  // PY-27148
  public void testTypingNTMake() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   "from typing import NamedTuple\n" +
                   "class Cat(NamedTuple):\n" +
                   "    name: str\n" +
                   "    age: int\n" +
                   "expr = Cat(\"name\", 5)._make([\"newname\", 6])")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   "from typing import NamedTuple\n" +
                   "class Cat(NamedTuple):\n" +
                   "    name: str\n" +
                   "    age: int\n" +
                   "expr = Cat._make([\"newname\", 6])")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   "from typing import NamedTuple\n" +
                   "Cat = NamedTuple(\"Cat\", name=str, age=int)\n" +
                   "expr = Cat(\"name\", 5)._make([\"newname\", 6])")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   "from typing import NamedTuple\n" +
                   "Cat = NamedTuple(\"Cat\", name=str, age=int)\n" +
                   "expr = Cat._make([\"newname\", 6])")
    );
  }

  // PY-27148
  public void testCollectionsNTReplace() {
    doTest("Cat",
           "from collections import namedtuple\n" +
           "Cat = namedtuple(\"Cat\", \"name age\")\n" +
           "expr = Cat(\"name\", 5)._replace(name=\"newname\")");

    doTest("Cat",
           "from collections import namedtuple\n" +
           "class Cat(namedtuple(\"Cat\", \"name age\")):\n" +
           "    pass\n" +
           "expr = Cat(\"name\", 5)._replace(name=\"newname\")");

    doTest("str",
           "from collections import namedtuple\n" +
           "Cat = namedtuple(\"Cat\", \"name age\")\n" +
           "expr = Cat(\"name\", 5)._replace(age=\"five\").age");

    doTest("Cat",
           "from collections import namedtuple\n" +
           "class Cat(namedtuple(\"Cat\", \"name age\")):\n" +
           "    pass\n" +
           "expr = Cat._replace(Cat(\"name\", 5), name=\"newname\")");
  }

  // PY-27148
  public void testTypingNTReplace() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   "from typing import NamedTuple\n" +
                   "class Cat(NamedTuple):\n" +
                   "    name: str\n" +
                   "    age: int\n" +
                   "expr = Cat(\"name\", 5)._replace(name=\"newname\")")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   "from typing import NamedTuple\n" +
                   "Cat = NamedTuple(\"Cat\", name=str, age=int)\n" +
                   "expr = Cat(\"name\", 5)._replace(name=\"newname\")")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("int",
                   "from typing import NamedTuple\n" +
                   "Cat = NamedTuple(\"Cat\", name=str, age=int)\n" +
                   "expr = Cat(\"name\", 5)._replace(age=\"give\").age")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   "from typing import NamedTuple\n" +
                   "class Cat(NamedTuple):\n" +
                   "    name: str\n" +
                   "    age: int\n" +
                   "expr = Cat._replace(Cat(\"name\", 5), name=\"newname\")")
    );
  }

  // PY-21302
  public void testNewTypeReferenceTarget() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("from typing import NewType\n" +
                                                  "UserId = NewType('UserId', int)\n" +
                                                  "expr = UserId");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyTypingNewType.class);
        }

        final PyExpression instance = parseExpr("from typing import NewType\n" +
                                                "UserId = NewType('UserId', int)\n" +
                                                "expr = UserId(12)");

        for (TypeEvalContext context : getTypeEvalContexts(instance)) {
          assertInstanceOf(context.getType(instance), PyTypingNewType.class);
        }
      }
    );
  }

  // PY-21302
  public void testNewType() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("UserId",
                   "from typing import NewType\n" +
                   "UserId = NewType('UserId', int)\n" +
                   "expr = UserId(12)")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Type[UserId]",
                   "from typing import Dict, NewType\n" +
                   "UserId = NewType('UserId', Dict[int, str])\n" +
                   "expr = UserId\n")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("(a: UserId) -> str",
                   "from typing import Dict, NewType\n" +
                   "UserId = NewType('UserId', int)\n" +
                   "def foo(a: UserId) -> str\n" +
                   "    pass\n" +
                   "expr = foo\n")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("UserId",
                   "from typing import NewType as nt\n" +
                   "UserId = nt('UserId', int)\n" +
                   "expr = UserId(12)\n")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("UserId",
                   "import typing\n" +
                   "UserId = typing.NewType('UserId', int)\n" +
                   "expr = UserId(12)\n")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("UserId",
                   "import typing as t\n" +
                   "UserId = t.NewType('UserId', int)\n" +
                   "expr = UserId(12)\n")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("SuperId",
                   "from typing import NewType\n" +
                   "UserId = NewType('UserId', int)\n" +
                   "SuperId = NewType('SuperId', UserId)\n" +
                   "expr = SuperId(UserId(12))\n")
    );
  }

  // PY-26992
  public void testImportedOrderedDict() {
    doTest("OrderedDict[str, str]",
           "from collections import OrderedDict\n" +
           "expr = OrderedDict((('name', 'value'), ('another_name', 'another_value')))");
  }

  // PY-26992
  public void testFullyQualifiedOrderedDict() {
    doTest("OrderedDict[str, str]",
           "import collections\n" +
           "expr = collections.OrderedDict((('name', 'value'), ('another_name', 'another_value')))");
  }

  // PY-26628
  public void testGenericTypingProtocolExt() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> doTest("int",
                   "from typing_extensions import Protocol\n" +
                   "from typing import TypeVar\n" +
                   "T = TypeVar(\"T\")\n" +
                   "class MyProto1(Protocol[T]):\n" +
                   "    def func(self) -> T:\n" +
                   "        pass\n" +
                   "class MyClass1(MyProto1[int]):\n" +
                   "    pass\n" +
                   "expr = MyClass1().func()")
    );
  }

  // PY-9634
  public void testAfterIsInstanceAndAttributeUsage() {
    doTest("Union[int, {bar}]",
           "def bar(y):\n" +
           "    if isinstance(y, int):\n" +
           "        pass\n" +
           "    print(y.bar)" +
           "    expr = y");
  }

  // PY-28052
  public void testClassAttributeAnnotatedAsAny() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Any",
                   "from typing import Any\n" +
                   "\n" +
                   "\n" +
                   "class MyClass:\n" +
                   "    arbitrary: Any = 42\n" +
                   "\n" +
                   "\n" +
                   "expr = MyClass().arbitrary")
    );
  }

  // PY-13750
  public void testBuiltinRound() {
    doTest("float", "expr = round(1)");
    doTest("float", "expr = round(1, 1)");

    doTest("float", "expr = round(1.1)");
    doTest("float", "expr = round(1.1, 1)");

    doTest("float", "expr = round(True)");
    doTest("float", "expr = round(True, 1)");
  }

  // PY-28227
  public void testTypeVarTargetAST() {
    doTest("T",
           "from typing import TypeVar\n" +
           "expr = TypeVar('T')");
  }

  // PY-28227
  public void testTypeVarTargetStub() {
    doMultiFileTest("T",
                    "from a import T\n" +
                    "expr = T");
  }

  // PY-29748
  public void testAfterIdentityComparison() {
    doTest("int",
           "a = 1\n" +
           "if a is a:\n" +
           "   expr = a");
  }

  // PY-31956
  public void testInAndNotBoolContains() {
    doTest("bool",
                   "class MyClass:\n" +
                   "    def __contains__(self):\n" +
                   "        return 42\n" +
                   "\n" +
                   "expr = 1 in MyClass()");
  }

  // PY-32533
  public void testSuperWithAnotherType() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("A",
                   "class A:\n" +
                   "    def f(self):\n" +
                   "        return 'A'\n" +
                   "\n" +
                   "class B:\n" +
                   "    def f(self):\n" +
                   "        return 'B'\n" +
                   "\n" +
                   "class C(B):\n" +
                   "    def f(self):\n" +
                   "        return 'C'\n" +
                   "\n" +
                   "class D(C, A):\n" +
                   "    def f(self):\n" +
                   "        expr = super(B, self)\n" +
                   "        return expr.f()")
    );
  }

  // PY-32113
  public void testAssertionOnVariableFromOuterScope() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("D",
                   "class B: pass\n" +
                   "\n" +
                   "class D(B): pass\n" +
                   "\n" +
                   "g_b: B = undefined\n" +
                   "\n" +
                   "def main() -> None:\n" +
                   "    assert isinstance(g_b, D)\n" +
                   "    expr = g_b")
    );
  }

  // PY-32113
  public void testAssertionFunctionFromOuterScope() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("B",
                   "class B: pass\n" +
                   "\n" +
                   "def g_b():\n" +
                   "    pass\n" +
                   "\n" +
                   "def main() -> None:\n" +
                   "    assert isinstance(g_b, B)\n" +
                   "    expr = g_b")
    );
  }

  // PY-33886
  public void testAssignmentExpressions() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> {
        doTest("int", "[expr := 1]");
        doTest("int", "[expr := (1)]");
        doTest("int", "expr = (e := 1)");
        doTest("int", "foo(expr := 1)");
        doMultiFileTest("Type[A]", "from a import member\nexpr = member");

        assertNull(((PyTargetExpression)parseExpr("(nums := [0 for expr in range(10)])")).findAssignedValue());
      }
    );
  }

  // PY-34945
  public void testFinal() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        doTest("int",
               "from typing_extensions import Final\n" +
               "expr: Final[int] = undefined");

        doTest("int",
               "from typing_extensions import Final\n" +
               "expr: Final = 5");

        doTest("int",
               "from typing_extensions import Final\n" +
               "expr: Final[int]");
      }
    );

    doTest("int",
           "from typing_extensions import Final\n" +
           "expr = undefined  # type: Final[int]");

    doTest("int",
           "from typing_extensions import Final\n" +
           "expr = 5  # type: Final");
  }

  // PY-35235
  public void testTypingLiteral() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        doTest("Literal[True]",
               "from typing_extensions import Literal\n" +
               "expr: Literal[True] = False");

        doTest("bool",
               "from typing_extensions import Literal\n" +
               "expr: Literal[] = False");

        doTest("bool",
               "from typing_extensions import Literal\n" +
               "expr: Literal = False");

        doTest("bool",
               "expr = False");
      }
    );

    doTest("Literal[10]",
           "from typing_extensions import Literal\n" +
           "expr = 20  # type: Literal[10]");

    doTest("Literal[-10]",
           "from typing_extensions import Literal\n" +
           "expr = 20  # type: Literal[-10]");

    doTest("int",
           "from typing_extensions import Literal\n" +
           "expr = 20  # type: Literal[10.5]");

    doTest("int",
           "from typing_extensions import Literal\n" +
           "expr = 20  # type: Literal[10j]");

    doTest("int",
           "from typing_extensions import Literal\n" +
           "expr = 20  # type: Literal[]");

    doTest("int",
           "from typing_extensions import Literal\n" +
           "expr = 20  # type: Literal");

    doTest("int",
           "from typing_extensions import Literal\n" +
           "expr = 20");
  }

  // PY-35235
  public void testTypingLiteralNone() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("None",
                   "from typing_extensions import Literal\n" +
                   "expr: Literal[None] = undefined")
    );
  }

  // PY-35235
  public void testTypingLiteralEnum() {
    // we don't support using `typing.Literal` with enums :(
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doMultiFileTest("A",
                            "from typing_extensions import Literal\n" +
                            "\n" +
                            "from enum import Enum\n" +
                            "\n" +
                            "class A(Enum):\n" +
                            "    V1 = 1\n" +
                            "    V2 = 2\n" +
                            "\n" +
                            "expr: Literal[A.V1] = undefined")
    );
  }

  // PY-35235
  public void testUnionOfTypingLiterals() {
    doTest("Literal[-1, 0, 1]",
           "from typing_extensions import Literal\n" +
           "expr = undefined  # type: Literal[-1, 0, 1]");

    doTest("Literal[42, \"foo\", True]",
           "from typing_extensions import Literal\n" +
           "expr = undefined  # type: Literal[42, \"foo\", True]");
  }

  // PY-35235
  public void testTypingLiteralOfTypingLiterals() {
    doTest("Literal[1, 2, 3, 4, 5]",
           "from typing_extensions import Literal\n" +
           "a = Literal[1]\n" +
           "b = Literal[2, 3]\n" +
           "c = Literal[4, 5]\n" +
           "d = Literal[b, c]\n" +
           "expr = undefined  # type: Literal[a, d]");

    doTest("Union[Literal[1, 2, \"foo\", 5], None]",
           "from typing_extensions import Literal\n" +
           "expr = undefined  # type: Literal[Literal[Literal[1, 2], \"foo\"], 5, None]");
  }

  // PY-40838
  public void testUnionOfManyTypesInclLiterals() {
    doTest("Union[Literal[\"1\", 2], bool, None]",
           "from typing import overload, Literal\n" +
           "\n" +
           "@overload\n" +
           "def foo1() -> Literal[\"1\"]:\n" +
           "    pass\n" +
           "\n" +
           "@overload\n" +
           "def foo1() -> Literal[2]:\n" +
           "    pass\n" +
           "\n" +
           "@overload\n" +
           "def foo1() -> bool:\n" +
           "    pass\n" +
           "\n" +
           "@overload\n" +
           "def foo1() -> None:\n" +
           "    pass\n" +
           "\n" +
           "def foo1()\n" +
           "    pass\n" +
           "\n" +
           "expr = foo1()");
  }

  // PY-35235
  public void testOverloadsWithTypingLiteral() {
    final String prefix = "from typing_extensions import Literal\n" +
                          "from typing import overload\n" +
                          "\n" +
                          "@overload\n" +
                          "def foo(p1: Literal[\"a\"]) -> str: ...\n" +
                          "\n" +
                          "@overload\n" +
                          "def foo(p1: Literal[\"b\"]) -> bytes: ...\n" +
                          "\n" +
                          "@overload\n" +
                          "def foo(p1: str) -> int: ...\n" +
                          "\n" +
                          "def foo(p1):\n" +
                          "    pass\n" +
                          "\n";

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        doTest("Union[str, int]",
               prefix +
               "a: Literal[\"a\"]\n" +
               "expr = foo(a)");

        doTest("int",
               prefix +
               "a = \"a\"\n" +
               "expr = foo(a)");

        doTest("Union[str, int]",
               prefix +
               "expr = foo(\"a\")");
      }
    );
  }

  // PY-33651
  public void testSlicingHomogeneousTuple() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("Tuple[int, ...]",
                   "from typing import Tuple\n" +
                   "x: Tuple[int, ...]\n" +
                   "expr = x[0:]")
    );
  }

  public void testAnnotatedClsReturnOverloadedClassMethod() {
    doMultiFileTest("mytime",
                    "from mytime import mytime\n" +
                    "expr = mytime.now()");
  }

  // PY-36008
  public void testTypedDict() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("A",
               "from typing import TypedDict\n" +
               "class A(TypedDict):\n" +
               "    x: int\n" +
               "a: A = {'x': 42}\n" +
               "expr = a");
      }
    );
  }

  // PY-33663
  public void testAnnotatedSelfReturnProperty() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("A",
                   "from typing import TypeVar\n" +
                   "\n" +
                   "T = TypeVar(\"T\")\n" +
                   "\n" +
                   "class A:\n" +
                   "    @property\n" +
                   "    def foo(self: T) -> T:\n" +
                   "        pass\n" +
                   "\n" +
                   "expr = A().foo")
    );
  }

  // PY-30861
  public void testDontReplaceSpecifiedReturnTypeWithSelf() {
    doTest("dict",
           "from collections import defaultdict\n" +
           "data = defaultdict(dict)\n" +
           "expr = data['name']");
  }

  // PY-37601
  public void testClassWithOwnInitInheritsClassWithGenericCall() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("Derived",
                   "from typing import Any, Generic, TypeVar\n" +
                   "\n" +
                   "T = TypeVar(\"T\")\n" +
                   "\n" +
                   "class Base(Generic[T]):\n" +
                   "    def __call__(self, p: Any) -> T:\n" +
                   "        pass\n" +
                   "\n" +
                   "class Derived(Base):\n" +
                   "    def __init__():\n" +
                   "        pass\n" +
                   "\n" +
                   "expr = Derived()")
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpression() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int",
               "from typing import TypedDict\n" +
               "class A(TypedDict):\n" +
               "    x: int\n" +
               "a: A = {'x': 42}\n" +
               "expr = a['x']");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionUndefinedKey() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("Any",
               "from typing import TypedDict\n" +
               "class A(TypedDict):\n" +
               "    x: int\n" +
               "a: A = {'x': 42}\n" +
               "expr = a[x]");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionRequiredKey() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int",
               "from typing import TypedDict\n" +
               "class A(TypedDict):\n" +
               "    x: int\n" +
               "a: A = {'x': 42}\n" +
               "expr = a.get('x')");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionOptionalKey() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("Optional[int]",
               "from typing import TypedDict\n" +
               "class A(TypedDict, total=False):\n" +
               "    x: int\n" +
               "a: A = {'x': 42}\n" +
               "expr = a.get('x')");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionSameValueTypeAndDefaultArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int",
               "from typing import TypedDict\n" +
               "class A(TypedDict, total=False):\n" +
               "    x: int\n" +
               "a: A = {'x': 42}\n" +
               "expr = a.get('x', 42)");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionDifferentValueTypeAndDefaultArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("Union[int, str]",
               "from typing import TypedDict\n" +
               "class A(TypedDict, total=False):\n" +
               "    x: int\n" +
               "a: A = {'x': 42}\n" +
               "expr = a.get('x', '')");
      }
    );
  }

  // PY-36008
  public void testTypedDictAlternativeSyntax() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("A",
               "from typing import TypedDict\n" +
               "A = TypedDict('A', {'x': int}, total=False)\n" +
               "expr = A");
      }
    );
  }

  // PY-37678
  public void testDataclassesReplace() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doMultiFileTest("Foo",
                            "import dataclasses as dc\n" +
                            "\n" +
                            "@dc.dataclass\n" +
                            "class Foo:\n" +
                            "    x: int\n" +
                            "    y: int\n" +
                            "\n" +
                            "foo = Foo(1, 2)\n" +
                            "expr = dc.replace(foo, x=3)")
    );
  }

  // PY-35881
  public void testResolveToAnotherFileClassWithBuiltinNameField() {
    doMultiFileTest(
      "int",
      "from foo import Foo\n" +
      "foo = Foo(0)\n" +
      "expr = foo.id"
    );
  }

  // PY-35885
  public void testFunctionDunderDoc() {
    doTest("str",
           "def example():\n" +
           "    \"\"\"Example Docstring\"\"\"\n" +
           "    return 0\n" +
           "expr = example.__doc__");
  }

  // PY-38786
  public void testParticularTypeAgainstTypeVarBoundedWithBuiltinType() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("Type[MyClass]",
                   "from typing import TypeVar, Type\n" +
                   "\n" +
                   "T = TypeVar(\"T\", bound=type)\n" +
                   "\n" +
                   "def foo(t: T) -> T:\n" +
                   "    pass\n" +
                   "\n" +
                   "class MyClass:\n" +
                   "    pass\n" +
                   "\n" +
                   "expr = foo(MyClass)")
    );
  }

  // PY-38786
  public void testDunderSubclasses() {
    doTest("List[Type[Base]]",
           "class Base(object):\n" +
           "    pass\n" +
           "expr = Base.__subclasses__()");
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

  private void checkTypes(@NotNull String expectedType, @Nullable PyExpression expr) {
    assertNotNull(expr);
    for (TypeEvalContext context : getTypeEvalContexts(expr)) {
      assertType(expectedType, expr, context);
      assertProjectFilesNotParsed(context);
    }
  }

  public static final String TEST_DIRECTORY = "/types/";

  private void doMultiFileTest(@NotNull  final String expectedType, @NotNull final String text) {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    checkTypes(expectedType, parseExpr(text));
  }
}
