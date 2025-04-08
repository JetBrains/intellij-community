// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.google.common.collect.ImmutableList;
import com.intellij.testFramework.LightProjectDescriptor;
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


public class PyTypeTest extends PyTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  /**
   * Call of union returns union of all callable types in this union
   */
  public void testCallableInUnion() {
    doTest("str",
           """
             import random
             def spam():
                 return "D"
             class Eggs:
                 pass
             class Eggs2:
                 pass
             dd = spam if random.randint != 42 else Eggs2()
             var = dd if random.randint != 42 else dd
             expr = var()""");
  }

  public void testTupleType() {
    doTest("str",
           "t = ('a', 2)\n" +
           "expr = t[0]");
    doTest("List[bool]",
           """
             from typing import List, Literal
             def foo(t: tuple[int, str, List[bool]], i: Literal[2]):
                 expr = t[i]""");
    doTest("Union[int, List[bool]]",
           """
             from typing import List, Literal
             def foo(t: tuple[int, str, List[bool]], i: Literal[0, -1]):
                 expr = t[i]""");
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
           """
             class C(object):
                 def __add__(self, other):
                     return -1
             c = C()
             x = c + 'foo'
             expr = x + 'bar'""");
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
           """
             def x(b):
               if b:
                 return (1, 'a')
               else:
                 return ('a', 1)
             expr = x()""");
  }

  public void testAugAssignment() {
    doTest("int",
           """
             def x():
                 count = 0
                 count += 1
                 return count
             expr = x()""");
  }

  public void testSetComp() {
    doTest("Set[int]",
           "expr = {i for i in range(3)}");
  }

  public void testSet() {
    doTest("Set[int]",
           "expr = {1, 2, 3}");
  }

  // PY-1425
  public void testNone() {
    doTest("Any",
           """
             class C:
                 def __init__(self): self.foo = None
             expr = C().foo""");
  }

  // PY-1427
  public void testUnicodeLiteral() {  // PY-1427
    doTest("unicode",
           "expr = u'foo'");
  }

  public void testPropertyType() {
    doTest("property",
           """
             class C:
                 x = property(lambda self: 'foo', None, None)
             expr = C.x
             """);
  }

  public void testPropertyInstanceType() {
    doTest("str",
           """
             class C:
                 x = property(lambda self: 'foo', None, None)
             c = C()
             expr = c.x
             """);
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
      """
        class RectangleFactory(object):
            def __getitem__(self, item):
                return 1
        factory = RectangleFactory()
        expr = factory[:]"""
    );
  }

  public void testExceptType() {
    doTest("ImportError",
           """
             try:
                 pass
             except ImportError, expr:
                 pass""");
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
                   """
                     def foo(x) -> list:
                         return x
                     expr = foo(None)""")
    );
  }

  public void testRestParamType() {
    doTest("int",
           """
             def foo(limit):
               ''':param integer limit: maximum number of stack frames to show'''
               expr = limit""");
  }

  // PY-3849
  public void testRestClassType() {
    doTest("Foo",
           """
             class Foo: pass
             def foo(limit):
               ''':param :class:`Foo` limit: maximum number of stack frames to show'''
               expr = limit""");
  }

  public void testRestIvarType() {
    doTest("str",
           """
             def foo(p):
                 var = p.bar
                 ''':type var: str'''
                 expr = var""");
  }

  public void testUnknownTypeInUnion() {
    doTest("Union[int, Any]",
           """
             def f(c, x):
                 if c:
                     return 1
                 return x
             expr = f(1, g())
             """);
  }

  public void testIsInstance() {
    doTest("str",
           """
             def f(c):
                 def g():
                     '''
                     :rtype: int or str
                     '''
                 x = g()
                 if isinstance(x, str):
                     expr = x""");
  }

  // PY-2140
  public void testNotIsInstance() {
    doTest("list",
           """
             def f(c):
                 def g():
                     '''
                     :rtype: int or str or list
                     '''
                 x = g()
                 if not isinstance(x, (str, long)):
                     expr = x""");
  }

  public void testIsInstance2() {
    doTest("str",
           """
             x = ""
             if isinstance(x, (1, "")):
                 expr = x
             """);
  }

  public void testIfIsInstanceOr1() {
    doTest("Union[str, int]",
           """
               def foo(a):
                   if isinstance(a, int) or isinstance(a, str):
                       expr = a
           """);
  }

  public void testIfIsInstanceOr2() {
    doTest("Union[B, A, int, str]",
           """
           class A:
               pass
           
           class B:
               pass
          
           def f(a: object):
               if isinstance(a, str) or isinstance(a, int) or isinstance(a, A) or isinstance(a, B):
                   expr = a
               else:
                   pass
           """);
  }

  public void testIfIsInstanceAnd1() {
    doTest("A",
           """
             class A:
                 pass
             
             def f(a):
                 if isinstance(a, (str, A)) and isinstance(a, (A, int)):
                     expr = a
             """);
  }

  public void testIfIsInstanceAnd2() {
    doTest("A",
           """
             class A:
                 pass
             
             class B:
                 pass
             
             def f(a):
                 if isinstance(a, (str, A)) and isinstance(a, (A, int)) and isinstance(a, (B, A)):
                     expr = a
             """);
  }

  public void testIfIsInstanceLogicalExpressions() {
    doTest("Union[B, str]",
           """
             class A:
                 pass
             
             class B:
                 pass
             
             def f(a):
                 if isinstance(a, (str, A, int)) and not isinstance(a, (A, int)) or isinstance(a, B):
                     expr = a
             """);
  }

  // PY-4383
  public void testAssertIsInstance() {
    doTest("int",
           """
             from unittest import TestCase

             class Test1(TestCase):
                 def test_1(self, c):
                     x = 1 if c else 'foo'
                     self.assertIsInstance(x, int)
                     expr = x
             """);
  }

  // PY-20679
  public void testIsInstanceViaTrue() {
    doTest("str",
           """
             a = None
             if isinstance(a, str) is True:
                 expr = a
             raise TypeError('Invalid type')""");

    doTest("str",
           """
             a = None
             if True is isinstance(a, str):
                 expr = a
             raise TypeError('Invalid type')""");
  }

  // PY-20679
  public void testIsInstanceViaFalse() {
    doTest("str",
           """
             a = None
             if isinstance(a, str) is not False:
                 expr = a
             raise TypeError('Invalid type')""");

    doTest("str",
           """
             a = None
             if False is not isinstance(a, str):
                 expr = a
             raise TypeError('Invalid type')""");

    doTest("str",
           """
             a = None
             if not isinstance(a, str) is False:
                 expr = a
             raise TypeError('Invalid type')""");

    doTest("str",
           """
             a = None
             if not False is isinstance(a, str):
                 expr = a
             raise TypeError('Invalid type')""");
  }

  // PY-20679
  public void testNotIsInstanceViaTrue() {
    doTest("str",
           """
             a = None
             if not isinstance(a, str) is True:
                 raise TypeError('Invalid type')
             expr = a""");

    doTest("str",
           """
             a = None
             if not True is isinstance(a, str):
                 raise TypeError('Invalid type')
             expr = a""");

    doTest("str",
           """
             a = None
             if isinstance(a, str) is not True:
                 raise TypeError('Invalid type')
             expr = a""");

    doTest("str",
           """
             a = None
             if True is not isinstance(a, str):
                 raise TypeError('Invalid type')
             expr = a""");
  }

  // PY-20679
  public void testNotIsInstanceViaFalse() {
    doTest("str",
           """
             a = None
             if isinstance(a, str) is False:
                 raise TypeError('Invalid type')
             expr = a""");

    doTest("str",
           """
             a = None
             if False is isinstance(a, str):
                 raise TypeError('Invalid type')
             expr = a""");
  }

  public void testIfNotEqOperator() {
    doTest("Literal[\"ab\"]",
           """
             from typing import Literal
             def foo(v: Literal["abba", "ab"]):
                 if (v <> "abba"):
                     expr = v
             """);
  }

  // PY-4279
  public void testFieldReassignment() {
    doTest("C1",
           """
             class C1(object):
                 def m1(self):
                     pass

             class C2(object):
                 def m2(self):
                     pass

             class Test(object):
                 def __init__(self, param1):
                     self.x = param1
                     self.x = C1()
                     expr = self.x
             """);
  }

  public void testSOEOnRecursiveCall() {
    doTest("Any", "def foo(x): return foo(x)\n" +
                  "expr = foo(1)");
  }

  public void testGenericConcrete() {
    doTest("int", """
      def f(x):
          '''
          :type x: T
          :rtype: T
          '''
          return x

      expr = f(1)
      """);
  }

  public void testGenericConcreteMismatch() {
    doTest("int", """
      def f(x, y):
          '''
          :type x: T
          :rtype: T
          '''
          return x

      expr = f(1)
      """);
  }

  // PY-5831
  public void testYieldType() {
    doTest("Any", """
      def f():
          expr = yield 2
      """);
  }

  // PY-9590
  public void testYieldParensType() {
    doTest("Any", """
      def f():
          expr = (yield 2)
      """);
  }

  public void testFunctionAssignment() {
    doTest("int",
           """
             def f():
                 return 1
             g = f
             h = g
             expr = h()
             """);
  }

  public void testPropertyOfUnionType() {
    doTest("int", """
      def f():
          '''
          :rtype: int or slice
          '''
          raise NotImplementedError

      x = f()
      expr = x.bit_length()
      """);
  }

  public void testUndefinedPropertyOfUnionType() {
    doTest("Any", """
      x = 42 if True else 'spam'
      expr = x.foo
      """);
  }

  // PY-7058
  public void testReturnTypeOfTypeForInstance() {
    PyExpression expr = parseExpr("""
                                    class C(object):
                                        pass

                                    x = C()
                                    expr = type(x)
                                    """);
    assertNotNull(expr);
    for (TypeEvalContext context : getTypeEvalContexts(expr)) {
      PyType type = context.getType(expr);
      assertInstanceOf(type, PyClassType.class);
      assertTrue("Got instance type instead of class type", ((PyClassType)type).isDefinition());
    }
  }

  // PY-7058
  public void testReturnTypeOfTypeForClass() {
    doTest("type", """
      class C(object):
          pass

      expr = type(C)
      """);
  }

  // PY-7058
  public void testReturnTypeOfTypeForUnknown() {
    doTest("Any", """
      def f(x):
          expr = type(x)
      """);
  }

  // PY-7040
  public void testInstanceAndClassAttribute() {
    doTest("int",
           """
             class C(object):
                 foo = 'str1'

                 def __init__(self):
                     self.foo = 3
                     expr = self.foo
             """);
  }

  // PY-7215
  public void testFunctionWithNestedGenerator() {
    doTest("List[int]",
           """
             def f():
                 def g():
                     yield 10
                 return list(g())

             expr = f()
             """);
  }

  public void testGeneratorNextType() {
    doTest("int",
           """
             def f():
                 yield 10
             expr = f().next()
             """);
  }

  public void testGeneratorFunctionType() {
    doTest("Generator[str, Any, int]",
           """
             def f():
                 yield 'foo'
                 return 0

             expr = f()
             """);
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
           """
             xs = (str(x) for x in range(10))
             for expr in xs:
                 pass
             """);
  }

  // EA-40207
  public void testRecursion() {
    doTest("list",
           """
             def f():
                 return [f()]
             expr = f()
             """);
  }

  // PY-5084
  public void testIfIsInstanceElse() {
    doTest("str",
           """
             def test(c):
                 x = 'foo' if c else 42
                 if isinstance(x, int):
                     print(x)
                 else:
                     expr = x
             """);
  }

  // PY-5614
  public void testUnknownReferenceTypeAttribute() {
    doTest("str",
           """
             def f(x):
                 if isinstance(x.foo, str):
                     expr = x.foo
             """);
  }

  // PY-5614
  public void testUnknownTypeAttribute() {
    doTest("str",
           """
             class C(object):
                 def __init__(self, foo):
                     self.foo = foo
                 def f(self):
                     if isinstance(self.foo, str):
                         expr = self.foo
             """);
  }

  // PY-5614
  public void testKnownTypeAttribute() {
    doTest("str",
           """
             class C(object):
                 def __init__(self):
                     self.foo = 42
                 def f(self):
                     if isinstance(self.foo, str):
                         expr = self.foo
             """);
  }

  // PY-5614
  public void testNestedUnknownReferenceTypeAttribute() {
    doTest("str",
           """
             def f(x):
                 if isinstance(x.foo.bar, str):
                     expr = x.foo.bar
             """);

  }

  // PY-7063
  public void testDefaultParameterValue() {
    doTest("int",
           """
             def f(x, y=0):
                 return y
             expr = f(a, b)
             """);
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
    doTest("Any", """
      def f(x=None):
          expr = x
      """);
  }

  public void testParameterFromUsages() {
    final String text = """
      def foo(bar):
          expr = bar
      def use_foo(x):
          foo(x)
          foo(3)
          foo('bar')
      """;
    final PyExpression expr = parseExpr(text);
    assertNotNull(expr);
    doTest("Union[Union[int, str], Any]", expr, TypeEvalContext.codeCompletion(expr.getProject(), expr.getContainingFile()));
  }

  public void testUpperBoundGeneric() {
    doTest("Union[Union[int, str], Any]",
           """
             def foo(x):
                 '''
                 :type x: T <= int or str
                 :rtype: T
                 '''
             def bar(x):
                 expr = foo(x)
             """);
  }

  public void testIterationTypeFromGetItem() {
    doTest("int",
           """
             class C(object):
                 def __getitem__(self, index):
                     return 0
                 def __len__(self):
                     return 10
             for expr in C():
                 pass
             """);
  }

  public void testFunctionTypeAsUnificationArgument() {
    doTest("Union[List[int], str, unicode]",
           """
             def map2(f, xs):
                 '''
                 :type f: (T) -> V | None
                 :type xs: collections.Iterable[T] | str | unicode
                 :rtype: list[V] | str | unicode
                 '''
                 pass

             expr = map2(lambda x: 10, ['1', '2', '3'])
             """);
  }

  public void testFunctionTypeAsUnificationArgumentWithSubscription() {
    doTest("Union[int, str, unicode]",
           """
             def map2(f, xs):
                 '''
                 :type f: (T) -> V | None
                 :type xs: collections.Iterable[T] | str | unicode
                 :rtype: list[V] | str | unicode
                 '''
                 pass

             expr = map2(lambda x: 10, ['1', '2', '3'])[0]
             """);
  }

  public void testFunctionTypeAsUnificationResult() {
    doTest("int",
           """
             def f(x):
                 '''
                 :type x: T
                 :rtype: () -> T
                 '''
                 pass

             g = f(10)
             expr = g()
             """);
  }

  public void testUnionIteration() {
    doTest("Union[Union[int, str], Any]",
           """
             def f(c):
                 if c < 0:
                     return [1, 2, 3]
                 elif c == 0:
                     return 0.0
                 else:
                     return 'foo'

             def g(c):
                 for expr in f(c):
                     pass
             """);
  }

  public void testParameterOfFunctionTypeAndReturnValue() {
    doTest("int",
           """
             def func(f):
                 '''
                 :type f: (unknown) -> str
                 '''
                 return 1

             expr = func(foo)
             """);
  }

  // PY-6584
  public void testClassAttributeTypeInClassDocStringViaClass() {
    doTest("int",
           """
             class C(object):
                 '''
                 :type foo: int
                 '''
                 foo = None

             expr = C.foo
             """);
  }

  // PY-6584
  public void testClassAttributeTypeInClassDocStringViaInstance() {
    doTest("int",
           """
             class C(object):
                 '''
                 :type foo: int
                 '''
                 foo = None

             expr = C().foo
             """);
  }

  // PY-6584
  public void testInstanceAttributeTypeInClassDocString() {
    doTest("int",
           """
             class C(object):
                 '''
                 :type foo: int
                 '''
                 def __init__(self, bar):
                     self.foo = bar

             def f(x):
                 expr = C(x).foo
             """);
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

  public void testNoResolveToFunctionsInTypes() {
    doTest("Union[C, Any]",
           """
             class C(object):
                 def bar(self):
                     pass

             def foo(x):
                 '''
                 :type x: C | C.bar | foo
                 '''
                 expr = x
             """);
  }

  public void testIsInstanceExpressionResolvedToTuple() {
    doTest("Union[str, unicode]",
           """
             string_types = str, unicode

             def f(x):
                 if isinstance(x, string_types):
                     expr = x
             """);
  }

  public void testIsInstanceInConditionalExpression() {
    doTest("Union[str, int]",
           """
             def f(x):
                 expr = x if isinstance(x, str) else 10
             """);
  }

  // PY-9334
  public void testIterateOverListOfNestedTuples() {
    doTest("str",
           """
             def f():
                 for i, (expr, v) in [(0, ('foo', []))]:
                     print(expr)
             """);
  }

  // PY-8953
  public void testSelfInDocString() {
    doTest("int",
           """
             class C(object):
                 def foo(self):
                     '''
                     :type self: int
                     '''
                     expr = self
             """);
  }

  // PY-9605
  public void testPropertyReturnsCallable() {
    doTest("() -> int",
           """
             class C(object):
                 @property
                 def foo(self):
                     return lambda: 0

             c = C()
             expr = c.foo
             """);
  }

  public void testIterNext() {
    doTest("int",
           """
             xs = [1, 2, 3]
             expr = iter(xs).next()
             """);
  }

  // PY-10967
  public void testDefaultTupleParameterMember() {
    doTest("int",
           """
             def foo(xs=(1, 2)):
               expr, foo = xs
             """);
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
                   """
                     from typing import TypeVar, Tuple
                     T = TypeVar('T')
                     def foo(i: T) -> Tuple[T, ...]:
                         pass
                     expr = foo(5)""")
    );
  }

  public void testHeterogeneousTupleSubstitution() {
    doTest("tuple[int, int]",
           """
             def foo(i):
                 ""\"
                 :type i: T
                 :rtype: tuple[T, T]
                 ""\"
                 pass
             expr = foo(5)""");
  }

  public void testUnknownTupleSubstitution() {
    doTest("tuple",
           """
             def foo(i):
                 ""\"
                 :type i: T
                 :rtype: tuple
                 ""\"
                 pass
             expr = foo(5)""");
  }

  public void testTupleIterationType() {
    doTest("Union[int, str]",
           """
             xs = (1, 'a')
             for expr in xs:
                 pass
             """);
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

  // PY-38928
  public void testIterateListOfTuples() {
    doTest(
      "str",
      """
        for ((_, expr)) in [(1, 'foo')]:
            pass
        """
    );
  }

  public void testConstructorUnification() {
    doTest("C[int]",
           """
             class C(object):
                 def __init__(self, x):
                     '''
                     :type x: T
                     :rtype: C[T]
                     '''
                     pass

             expr = C(10)
             """);
  }

  public void testGenericClassMethodUnification() {
    doTest("int",
           """
             class C(object):
                 def __init__(self, x):
                     '''
                     :type x: T
                     :rtype: C[T]
                     '''
                     pass
                 def foo(self):
                     '''
                     :rtype: T
                     '''
                     pass

             expr = C(10).foo()
             """);
  }

  public void testGenericFunctionsUseSameTypeParameter() {
    doTest("int",
           """
             def id(x):
                 ""\"
                 :type x: T
                 :rtype: T
                 ""\"
                 return x

             def f3(x):
                 ""\"
                 :type x: T
                 ""\"
                 return id(x)
             expr = f3(42)""");
  }

  // PY-8836
  public void testNumpyArrayIntMultiplicationType() {
    doMultiFileTest("ndarray",
                    """
                      import numpy as np
                      expr = np.ones(10) * 2
                      """);
  }

  // PY-9439
  public void testNumpyArrayType() {
    doMultiFileTest("ndarray",
                    """
                      import numpy as np
                      expr = np.array([1,2,3])
                      """);
  }

  public void testUnionTypeAttributeOfDifferentTypes() {
    doTest("Union[list, int]",
           """
             class Foo:
                 x = []

             class Bar:
                 x = 42

             def f(c):
                 o = Foo() if c else Bar()
                 expr = o.x
             """);
  }

  // PY-11364
  public void testUnionTypeAttributeCallOfDifferentTypes() {
    doTest("Union[C1, C2]",
           """
             class C1:
                 def foo(self):
                     return self

             class C2:
                 def foo(self):
                     return self

             def f():
                 '''
                 :rtype: C1 | C2
                 '''
                 pass

             expr = f().foo()
             """);
  }

  // PY-12862
  public void testUnionTypeAttributeSubscriptionOfDifferentTypes() {
    doTest("Union[C1, C2]",
           """
             class C1:
                 def __getitem__(self, item):
                     return self

             class C2:
                 def __getitem__(self, item):
                     return self

             def f():
                 '''
                 :rtype: C1 | C2
                 '''
                 pass

             expr = f()[0]
             print(expr)
             """);
  }

  // PY-11541
  public void testIsInstanceBaseStringCheck() {
    doTest("Union[str, unicode]",
           """
             def f(x):
                 if isinstance(x, basestring):
                     expr = x
             """);
  }

  public void testStructuralType() {
    doTest("{foo, bar}",
           """
             def f(x):
                 x.foo + x.bar()
                 expr = x
             """);
  }

  public void testOnlyRelatedNestedAttributes() {
    doTest("{foo}",
           """
             def g(x):
                 x.bar

             def f(x, y):
                 x.foo + g(y)
                 expr = x
             """);
  }

  public void testNoContainsInContainsArgumentForStructuralType() {
    doTest("{foo, __getitem__}",
           """
             def f(x):
                x in []
                x.foo
                x[0]   expr = x
             """);
  }

  public void testStructuralTypeAndIsInstanceChecks() {
    doTest("(x: {foo}) -> None",
           """
             def f(x):
                 if isinstance(x, str):
                     x.lower()
                 x.foo

             expr = f
             """);
  }

  // PY-20832
  public void testStructuralTypeWithDunderIter() {
    doTest("{__iter__}",
           """
             def expand(values1):
                 for a in values1:
                     print(a)
                 expr = values1
             """);
  }

  // PY-20833
  public void testStructuralTypeWithDunderLen() {
    doTest("{__len__}",
           """
             def expand(values1):
                 a = len(values1)
                 expr = values1
             """);
  }

  // PY-16267
  public void testGenericField() {
    doTest("str",
           """
             class D(object):
                 def __init__(self, foo):
                     '''
                     :type foo: T
                     :rtype: D[T]
                     '''
                     self.foo = foo


             def g():
                 '''
                 :rtype: D[str]
                 '''
                 return D('test')


             y = g()
             expr = y.foo
             """);
  }

  public void testConditionInnerScope() {
    doTest("Union[str, int]",
           """
             if something:
                 foo = 'foo'
             else:
                 foo = 0

             expr = foo
             """);
  }

  public void testConditionOuterScope() {
    doTest("Union[str, int]",
           """
             if something:
                 foo = 'foo'
             else:
                 foo = 0

             def f():
                 expr = foo
             """);
  }

  // PY-18217
  public void testConditionImportOuterScope() {
    doMultiFileTest("Union[str, int]",
                    """
                      if something:
                          from m1 import foo
                      else:
                          from m2 import foo

                      def f():
                          expr = foo
                      """);
  }

  // PY-18402
  public void testConditionInImportedModule() {
    doMultiFileTest("Union[int, str]",
                    """
                      from m1 import foo

                      def f():
                          expr = foo
                      """);
  }

  // PY-18254
  public void testFunctionTypeCommentInStubs() {
    doMultiFileTest("MyClass",
                    """
                      from module import func

                      expr = func()""");
  }

  // PY-19967
  public void testInheritedNamedTupleReplace() {
    PyExpression expr = parseExpr("""
                                    from collections import namedtuple
                                    class MyClass(namedtuple('T', 'a b c')):
                                        def get_foo(self):
                                            return self.a

                                    inst = MyClass(1,2,3)
                                    expr = inst._replace(a=2)
                                    """);
    doTest("MyClass",
           expr,
           TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()));
  }

  public void testDictCallOnDictLiteralResult() {
    doTest("Dict[str, int]",
           "expr = dict({'a': 1})");
  }

  // PY-20063
  public void testIteratedSetElement() {
    doTest("int",
           """
             xs = {1}
             for expr in xs:
                 print(expr)""");
  }

  public void testIsNotNone() {
    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if x is not None:
                     expr = x
             """);

    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if None is not x:
                     expr = x
             """);

    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if not x is None:
                     expr = x
             """);

    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if not None is x:
                     expr = x
             """);
  }

  public void testIsNone() {
    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if x is None:
                     expr = x
             """);

    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if None is x:
                     expr = x
             """);
  }

  public void testAnyIsNone() {
    doTest("None",
           """
             def test_1(c):
               if c is None:
                 expr = c
             """);
  }

  public void testElseAfterIsNotNone() {
    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if x is not None:
                     print(x)
                 else:
                     expr = x
             """);

    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if None is not x:
                     print(x)
                 else:
                     expr = x
             """);

    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if not x is None:
                     print(x)
                 else:
                     expr = x
             """);

    doTest("None",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if not None is x:
                     print(x)
                 else:
                     expr = x
             """);
  }

  public void testElseAfterIsNone() {
    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if x is None:
                     print(x)
                 else:
                     expr = x
             """);

    doTest("int",
           """
             def test_1(self, c):
                 x = 1 if c else None
                 if None is x:
                     print(x)
                 else:
                     expr = x
             """);
  }

  public void testElseAfterAnyIsNone() {
    doTest("Any",
           """
             def test_1(c):
               if c is None:
                 print(c)
               else:
                 expr = c
             """);
  }

  // PY-21897
  public void testElseAfterIfReferenceStatement() {
    doTest("Any",
           """
             def test(a):
               if a:
                 print(a)
               else:
                 expr = a
             """);
  }

  public void testListLiteral() {
    doTest("list", "expr = []");

    doTest("List[int]", "expr = [1, 2, 3]");

    doTest("List[Union[str, int]]", "expr = ['1', 1, 1]");

    doTest("List[Union[Union[str, int], Any]]", "expr = ['1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]");
  }

  public void testSetLiteral() {
    doTest("Set[int]", "expr = {1}");

    doTest("Set[Union[str, int]]", "expr = {'1', 1, 1}");

    doTest("Set[Union[Union[str, int], Any]]", "expr = {'1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}");
  }

  public void testDictLiteral() {
    doTest("dict", "expr = {}");

    doTest("Dict[int, bool]", "expr = {1: False}");

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
           """
             class A:
                 pass
             class B(A):
                 def foo(self):
                     pass
             def test():
                 b = B()
                 assert(isinstance(b, A))
                 expr = b
             """);
  }

  // PY-20794
  public void testIterateOverPureList() {
    doTest("Any",
           """
             l = None  # type: list
             for expr in l:
                 print(expr)
             """);
  }

  // PY-20794
  public void testIterateOverDictValueWithDefaultValue() {
    doTest("Any",
           """
             d = None  # type: dict
             for expr in d.get('field', []):
                 print(expr['id'])
             """);
  }

  // PY-20797
  public void testValueOfEmptyDefaultDict() {
    doTest("list",
           """
             from collections import defaultdict
             expr = defaultdict(lambda: [])['x']
             """);
  }

  // PY-8473
  public void testCopyDotCopy() {
    doMultiFileTest("A",
                    """
                      import copy
                      class A(object):
                          pass
                      expr = copy.copy(A())
                      """);
  }

  // PY-8473
  public void testCopyDotDeepCopy() {
    doMultiFileTest("A",
                    """
                      import copy
                      class A(object):
                          pass
                      expr = copy.deepcopy(A())
                      """);
  }

  // PY-21083
  public void testFloatFromhex() {
    doTest("float",
           "expr = float.fromhex(\"0.5\")");
  }

  // PY-13159
  public void testAbsAbstractProperty() {
    doTest("str",
           """
             import abc
             class D:
                 @abc.abstractproperty
                 def foo(self):
                     return 'foo'
             expr = D().foo""");
  }

  public void testAbsAbstractPropertyWithFrom() {
    doTest("str",
           """
             from abc import abstractproperty
             class D:
                 @abstractproperty
                 def foo(self):
                     return 'foo'
             expr = D().foo""");
  }

  // TODO: enable this test when properties will be calculated with TypeEvalContext
  public void ignoredTestAbsAbstractPropertyWithAs() {
    doTest("str",
           """
             from abc import abstractproperty as ap
             class D:
                 @ap
                 def foo(self):
                     return 'foo'
             expr = D().foo""");
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
           """
             def get_value(v):
                 if v:
                     return min(v)
                 else:
                     return None
             expr = get_value([])""");
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
           """
             def foo(*args):
                 ""\"
                 :type args: int
                 ""\"
                 expr = args""");
  }

  // PY-19723
  public void testKeywordArgs() {
    doTest("Dict[str, int]",
           """
             def foo(**kwargs):
                 ""\"
                 :type kwargs: int
                 ""\"
                 expr = kwargs""");
  }

  // PY-19723
  public void testIterateOverKeywordArgs() {
    doTest("str",
           """
             def foo(**kwargs):
                 for expr in kwargs:
                     pass""");
  }

  // PY-19723
  public void testTypeVarSubstitutionInPositionalArgs() {
    doTest("int",
           """
             def foo(*args):  ""\"
               :type args: T
               :rtype: T
               ""\"
               pass
             expr = foo(1)""");
  }

  // PY-19723
  public void testTypeVarSubstitutionInHeterogeneousPositionalArgs() {
    doTest("Union[int, str]",
           """
             def foo(*args):  ""\"
               :type args: T
               :rtype: T
               ""\"
               pass
             expr = foo(1, "2")""");
  }

  // PY-19723
  public void testTypeVarSubstitutionInKeywordArgs() {
    doTest("int",
           """
             def foo(**kwargs):  ""\"
               :type kwargs: T
               :rtype: T
               ""\"
               pass
             expr = foo(a=1)""");
  }

  // PY-19723
  public void testTypeVarSubstitutionInHeterogeneousKeywordArgs() {
    doTest("Union[int, str]",
           """
             def foo(**kwargs):  ""\"
               :type kwargs: T
               :rtype: T
               ""\"
               pass
             expr = foo(a=1, b="2")""");
  }

  // PY-21474
  public void testReassigningOptionalListWithDefaultValue() {
    doTest("Union[List[str], list]",
           """
             def x(things):
                 ""\"
                 :type things: None | list[str]
                 ""\"
                 expr = things if things else []""");
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
           """
             def bug(foo):
                 ""\"
                 Args:
                     foo (list[int]|None): an optional list of ints\s
                 ""\"
                 if not foo:
                     return None
                 expr = foo""");
  }

  // PY-22037
  public void testAncestorPropertyReturnsSelf() {
    doTest("Child",
           """
             class Master(object):
                 @property
                 def me(self):
                     return self
             class Child(Master):
                 pass
             child = Child()
             expr = child.me""");
  }

  // PY-22181
  public void testIterationOverIterableWithSeparateIterator() {
    doTest("int",
           """
             class AIter(object):
                 def next(self):
                     return 5
             class A(object):
                 def __iter__(self):
                     return AIter()
             a = A()
             for expr in a:
                 print(expr)""");
  }

  public void testImportedPropertyResult() {
    doMultiFileTest("Any",
                    """
                      from .temporary import get_class
                      class Example:
                          def __init__(self):
                              expr = self.ins_class
                          @property
                          def ins_class(self):
                              return get_class()""");
  }

  // PY-7322
  public void testNamedTupleParameterInDocString() {
    doTest("Point",
           """
             from collections import namedtuple
             Point = namedtuple('Point', ('x', 'y'))
             def takes_a_point(point):
                 ""\"
                 :type point: Point
                 ""\"
                 expr = point""");
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
           """
             from typing import Union

             class A(object):
                 def __enter__(self):
                     return self

             class B(object):
                 def __enter__(self):
                     return self

             def f(x):
                 # type: (Union[A, B]) -> None
                 with x as expr:
                     pass""");
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
           """
             expr = []

             def fun():
                 global expr
                 expr""");

    doTest("list",
           """
             expr = []

             def fun():
                 def nuf():
                     global expr
                     expr""");

    doTest("list",
           """
             expr = []

             def fun():
                 expr = True
                \s
                 def nuf():
                     global expr
                     expr""");

    doTest("Union[bool, int]",
           """
             if True:
                 a = True
             else:
                 a = 5

             def fun():
                 def nuf():
                     global a
                     expr = a""");
  }

  // PY-37755
  public void testNonLocalType() {
    doTest("bool",
           """
             def fun():
                 expr = True

                 def nuf():
                     nonlocal expr
                     expr""");

    doTest("bool",
           """
             a = []

             def fun():
                 a = True

                 def nuf():
                     nonlocal a
                     expr = a""");

    doTest("Union[bool, int]",
           """
             a = []

             def fun():
                 if True:
                     a = True
                 else:
                     a = 5

                 def nuf():
                     nonlocal a
                     expr = a""");
  }

  // PY-21906
  public void testSOFOnTransitiveNamedTupleFields() {
    final PyExpression expression = parseExpr("""
                                                from collections import namedtuple
                                                class C:
                                                    FIELDS = ('a', 'b')
                                                FIELDS = C.FIELDS
                                                expr = namedtuple('Tup', FIELDS)""");

    getTypeEvalContexts(expression).forEach(context -> context.getType(expression));
  }

  // PY-22971
  public void testFirstOverloadAndImplementationInClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("int",
                   """
                     from typing import overload
                     class A:
                         @overload
                         def foo(self, value: int) -> int:
                             pass
                         @overload
                         def foo(self, value: str) -> str:
                             pass
                         def foo(self, value):
                             return None
                     expr = A().foo(5)""")
    );
  }

  // PY-22971
  public void testTopLevelFirstOverloadAndImplementation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("int",
                   """
                     from typing import overload
                     @overload
                     def foo(value: int) -> int:
                         pass
                     @overload
                     def foo(value: str) -> str:
                         pass
                     def foo(value):
                         return None
                     expr = foo(5)""")
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
                   """
                     from typing import overload
                     class A:
                         @overload
                         def foo(self, value: int) -> int:
                             pass
                         @overload
                         def foo(self, value: str) -> str:
                             pass
                         def foo(self, value):
                             return None
                     expr = A().foo("5")""")
    );
  }

  // PY-22971
  public void testTopLevelSecondOverloadAndImplementation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("str",
                   """
                     from typing import overload
                     @overload
                     def foo(value: int) -> int:
                         pass
                     @overload
                     def foo(value: str) -> str:
                         pass
                     def foo(value):
                         return None
                     expr = foo("5")""")
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
                   """
                     from typing import overload
                     class A:
                         @overload
                         def foo(self, value: int) -> int:
                             pass
                         @overload
                         def foo(self, value: str) -> str:
                             pass
                         def foo(self, value):
                             return None
                     expr = A().foo(object())""")
    );
  }

  // PY-22971
  public void testTopLevelNotMatchedOverloadsAndImplementation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Union[int, str]",
                   """
                     from typing import overload
                     @overload
                     def foo(value: int) -> int:
                         pass
                     @overload
                     def foo(value: str) -> str:
                         pass
                     def foo(value):
                         return None
                     expr = foo(object())""")
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
    doTest("int",
           "foo = bar() if 42 != 42 else [1, 2, 3, 4]\n" +
           "expr = foo[0]");
  }

  // PY-24364
  public void testReassignedParameter() {
    doTest("(entries: Any) -> Generator[Any, Any, None]",
           """
             def resort(entries):
                 entries = list(entries)
                 entries.sort(reverse=True)
                 for entry in entries:
                     yield entry
             expr = resort""");
  }

  public void testIsSubclass() {
    doTest("Type[A]",
           """
             class A: pass
             def foo(cls):
                 if issubclass(cls, A):
                     expr = cls""");
  }

  public void testIsSubclassWithTupleOfTypeObjects() {
    doTest("Type[Union[A, B]]",
           """
             class A: pass
             class B: pass
             def foo(cls):
                 if issubclass(cls, (A, B)):
                     expr = cls""");
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
           """
             class Foo:
                 @classmethod
                 def make_foo(cls, x: str) -> 'Foo':
                     pass
             expr = Foo.make_foo""");
  }

  public void testConstructingGenericClassWithNotFilledGenericValue() {
    doTest("MyIterator",
           """
             from typing import Iterator
             class MyIterator(Iterator[]):
                 def __init__(self) -> None:
                     self.other = "other"
             expr = MyIterator()""");
  }

  // PY-24923
  public void testEmptyNumpyFunctionDocstring() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () ->
      doTest("Any",
             """
               def f(param):
                   ""\"""\"
                   expr = param"""));
  }

  // PY-24923
  public void testEmptyNumpyClassDocstring() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () ->
      doTest("Any",
             """
               class C:
                   ""\"""\"
                   def __init__(self, param):
                       expr = param"""));
  }

  // PY-21175
  public void testNoneTypeFilteredOutByConditionalAssignment() {
    doTest("List[int]",
           """
             xs = None
             if xs is None:
                 xs = [1, 2, 3]
             expr = xs
             """);
  }

  // PY-21175
  public void testAnyAddedByConditionalDefinition() {
    doTest("Union[str, Any]",
           """
             def f(x, y):
                 if x:
                     var = y
                 else:
                     var = 'foo'
                 expr = var""");
  }

  // PY-21626
  public void testNestedConflictingIsNoneChecksInitialAny() {
    doTest("Optional[Any]",
           """
             def f(x):
                 if x is None:
                     if x is not None:
                         pass
                 expr = x""");
  }

  // PY-21626
  public void testNestedConflictingIsNoneChecksInitialKnown() {
    doTest("Optional[str]",
           """
             x = 'foo'
             if x is None:
                 if x is not None:
                     pass
             expr = x""");
  }

  // PY-21175
  public void testLazyAttributeInitialization() {
    doTest("Union[int, Any]",
           """
             class C:
                 def __init__(self):
                     self.attr = None
                \s
                 def m(self):
                     if self.attr is None:
                         self.attr = 42
                     expr = self.attr""");
  }

  // PY-21175
  public void testAssignmentToAttributeOfCallResultWithNameOfLocalVariable() {
    doTest("int",
           """
             def f(g):
                 x = 42
                 if True:
                     g().x = 'foo'
                 expr = x""");
  }

  public void testTypingNTInheritor() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("""
                                                    from typing import NamedTuple
                                                    class User(NamedTuple):
                                                        name: str
                                                        level: int = 0
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          final PyType type = context.getType(definition);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyClassType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from typing import NamedTuple
                                                  class User(NamedTuple):
                                                      name: str
                                                      level: int = 0
                                                  expr = User("name")""");

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
        final PyExpression definition = parseExpr("""
                                                    from typing import NamedTuple
                                                    User = NamedTuple("User", name=str, level=int)
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from typing import NamedTuple
                                                  User = NamedTuple("User", name=str, level=int)
                                                  expr = User("name")""");

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
        final PyExpression definition = parseExpr("""
                                                    from collections import namedtuple
                                                    class User(namedtuple("User", "name level")):
                                                        pass
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          final PyType type = context.getType(definition);
          assertInstanceOf(type, PyClassType.class);

          final List<PyClassLikeType> superClassTypes = ((PyClassType)type).getSuperClassTypes(context);
          assertEquals(1, superClassTypes.size());

          assertInstanceOf(superClassTypes.get(0), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from collections import namedtuple
                                                  class User(namedtuple("User", "name level")):
                                                      pass
                                                  expr = User('MrRobot')""");

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
        final PyExpression definition = parseExpr("""
                                                    from collections import namedtuple
                                                    User = namedtuple("User", "name level")
                                                    expr = User""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyNamedTupleType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from collections import namedtuple
                                                  User = namedtuple("User", "name level")
                                                  expr = User('MrRobot')""");

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
                   """
                     from collections import namedtuple
                     MyType1 = namedtuple('MyType1', 'x y')
                     MyType2 = namedtuple('MyType2', 'x y')
                     def foo(a: MyType1) -> MyType2:
                         pass
                     expr = foo""")
    );
  }

  // PY-25346
  public void testTypingNTInheritorField() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("int",
                   """
                     from typing import NamedTuple
                     class User(NamedTuple):
                         name: str
                         level: int = 0
                     expr = User("name").level""")
    );
  }

  // PY-25346
  public void testTypingNTTargetField() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("int",
                   """
                     from typing import NamedTuple
                     User = NamedTuple("User", name=str, level=int)
                     expr = User("name").level""")
    );
  }

  // PY-32240
  public void testTypingNTFunctionInheritorField() {
    doTest("str",
           """
             from typing import NamedTuple

             class A(NamedTuple("NT", [("user", str)])):
                 pass
                \s
             expr = A(undefined).user""");
  }

  // PY-4351
  public void testCollectionsNTInheritorField() {
    // Seems that this case won't be supported because
    // it requires to update ancestor, not class itself, for every `User(...)` call
    doTest("Any",
           """
             from collections import namedtuple
             class User(namedtuple("User", "name age")):
                 pass
             expr = User("name", 13).age""");
  }

  // PY-4351
  public void testCollectionsNTTargetField() {
    doTest("int",
           """
             from collections import namedtuple
             User = namedtuple("User", "name age")
             expr = User("name", 13).age""");
  }

  // PY-4351
  public void testTypingNTInheritorUnpacking() {
    doTest("int",
           """
             from typing import NamedTuple
             class User(NamedTuple("User", [("name", str), ("age", int)])):
                 pass
             y2, expr = User("name", 13)""");
  }

  // PY-4351
  public void testTypingNTTargetUnpacking() {
    doTest("int",
           """
             from typing import NamedTuple
             Point2 = NamedTuple('Point', [('x', int), ('y', str)])
             p2 = Point2(1, "1")
             expr, y2 = p2""");
  }

  // PY-29489
  public void testGenericIterableUnpackingNoBrackets() {
    doTest("int",
           """
             _, expr, _ = [1, 2, 3]
             """);
  }

  // PY-29489
  public void testGenericIterableUnpackingParentheses() {
    doTest("int",
           """
             (_, expr, _) = [1, 2, 3]
             """);
  }

  // PY-29489
  public void testGenericIterableUnpackingSquareBrackets() {
    doTest("int",
           """
             [_, expr] = [1, 2, 3]
             """);
  }

  // PY-29489
  public void testNonGenericIterableUnpacking() {
    doTest("str",
           """
             _, expr = "ab"
             """);
  }

  public void testUnpackingToNestedTargetsInSquareBracketsInAssignments() {
    doTest("int",
           """
             [_, [[expr], _]] = "foo", ((42,), "bar")
             """);
  }

  public void testUnpackingToNestedTargetsInSquareBracketsInForLoops() {
    doTest("str",
           """
             xs = [(1, ("foo",))]
             for [_, [expr]] in xs:
                 pass
             """);
  }

  public void testUnpackingToNestedTargetsInSquareBracketsInComprehensions() {
    doTest("str",
           """
             xs = [(1, ("foo",))]
             ys = [expr for [_, [expr]] in xs]
             """);
  }

  // PY-4351
  public void testCollectionsNTInheritorUnpacking() {
    // Seems that this case won't be supported because
    // it requires to update ancestor, not class itself, for every `User(...)` call
    doTest("Any",
           """
             from collections import namedtuple
             class User(namedtuple("User", "name ags")):
                 pass
             y1, expr = User("name", 13)""");
  }

  // PY-4351
  public void testCollectionsNTTargetUnpacking() {
    doTest("int",
           """
             from collections import namedtuple
             Point = namedtuple('Point', ['x', 'y'])
             p1 = Point(1, '1')
             expr, y1 = p1""");
  }

  // PY-18791
  public void testCallOnProperty() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Iterator[int]",
                   """
                     from typing import Iterator, Callable
                     class Foo:
                         def iterate(self) -> Iterator[int]:
                             pass
                         @property
                         def foo(self) -> Callable[[], Iterator[int]]:
                             return self.iterate
                     expr = Foo().foo()""")
    );
  }

  // PY-9662
  public void testBinaryExpressionWithUnknownOperand() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        doTest("Union[int, Any]",
               """
                 from typing import Any
                 x: Any
                 expr = x * 2""");

        doTest("Union[int, Any]",
               """
                 from typing import Any
                 x: Any
                 expr = 2 * x""");

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
  // TODO Re-enable once PY-61090 is fixed
  public void _testOperatorReturnsAny() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Union[bool, Any]",
                   """
                     from typing import Any
                     class Bar:
                         def __eq__(self, other) -> Any:
                             pass
                     expr = (Bar() == 2)""")
    );
  }

  // PY-24240
  public void testImplicitSuper() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        final PyExpression expression = parseExpr("""
                                                    class A:
                                                        pass
                                                    expr = A""");

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
                   """
                     class Foo:
                         def __init_subclass__(cls):
                             expr = cls""")
    );
  }

  // PY-27913
  public void testDunderClassGetItemFirstParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> doTest("Type[Foo]",
                   """
                     class Foo:
                         def __class_getitem__(cls, item):
                             expr = cls""")
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
                   """
                     from typing import Any, Dict
                     d: Dict[str, Dict[str, Any]]
                     expr = d["k"]""")
    );
  }

  // PY-26493
  public void testAssertAndStructuralType() {
    doTest("str",
           """
             def run_workloads(cfg):
                 assert isinstance(cfg, str)
                 cfg.split()
                 expr = cfg""");
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
                   """
                     from typing import TypeVar, Generic

                     T = TypeVar('T')
                     V = TypeVar('V')

                     class B(Generic[T]):
                         def f(self) -> T:
                             ...

                     class C(B[V], Generic[V]):
                         pass

                     expr = C().f()
                     """)
    );
  }

  // PY-26643
  public void testReplaceSelfInGenerator() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("Generator[B, Any, B]",
                   """
                     class A:
                         def foo(self):
                             yield self
                             return self
                     class B(A):
                         pass
                     expr = B().foo()""")
    );
  }

  public void testReplaceSelfInUnion() {
    doTest("Union[B, int]",
           """
             class A:
                 def foo(self, x):
                     if x:
                         return self
                     else:
                         return 1
             class B(A):
                 pass
             expr = B().foo(abc)""");
  }

  // PY-27143
  public void testReplaceInstanceInClassMethod() {
    doTest("Derived",
           """
             class Base:
                 @classmethod
                 def instance(cls):
                     return cls()
             class Derived(Base):
                 pass
             expr = Derived.instance()""");

    doTest("Derived",
           """
             class Base:
                 @classmethod
                 def instance(cls):
                     return cls()
             class Derived(Base):
                 pass
             expr = Derived().instance()""");
  }

  // PY-27143
  public void testReplaceInstanceInMethod() {
    doTest("Derived",
           """
             class Base:
                 def instance(self):
                     return self
             class Derived(Base):
                 pass
             expr = Derived.instance(Derived())""");

    doTest("Derived",
           """
             class Base:
                 def instance(self):
                     return self
             class Derived(Base):
                 pass
             expr = Derived().instance()""");
  }

  // PY-27143
  public void testReplaceDefinitionInClassMethod() {
    doTest("Type[Derived]",
           """
             class Base:
                 @classmethod
                 def cls(cls):
                     return cls
             class Derived(Base):
                 pass
             expr = Derived.cls()""");

    doTest("Type[Derived]",
           """
             class Base:
                 @classmethod
                 def cls(cls):
                     return cls
             class Derived(Base):
                 pass
             expr = Derived().cls()""");
  }

  // PY-27143
  public void testReplaceDefinitionInMethod() {
    doTest("Type[Derived]",
           """
             class Base:
                 def cls(self):
                     return self.__class__
             class Derived(Base):
                 pass
             expr = Derived.cls(Derived())""");

    doTest("Type[Derived]",
           """
             class Base:
                 def cls(self):
                     return self.__class__
             class Derived(Base):
                 pass
             expr = Derived().cls()""");
  }

  // PY-26992
  public void testInitializingInnerCallableClass() {
    doTest("B",
           """
             class A:
                 class B:
                     def __init__(self):
                         pass
                     def __call__(self, x):
                         pass
                 def __init__(self):
                     pass
             expr = A.B()""");
  }

  // PY-26992
  public void testInitializingInnerCallableClassThroughExplicitDunderInit() {
    doTest("B",
           """
             class A:
                 class B:
                     def __init__(self):
                         pass
                     def __call__(self, x):
                         pass
                 def __init__(self):
                     pass
             expr = A.B.__init__()""");
  }

  // PY-26992
  public void testInitializingInnerCallableClassThroughExplicitDunderNew() {
    doTest("B",
           """
             class A(object):
                 class B(object):
                     def __init__(self):
                         pass
                     def __call__(self, x):
                         pass
                 def __init__(self):
                     pass
             expr = A.B.__new__(A.B)""");
  }

  // PY-26973
  public void testSliceOnUnion() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Union[str, Any]",
                   """
                     from typing import Union
                     myvar: Union[str, int]
                     expr = myvar[0:3]""")
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
           """
             from collections import namedtuple
             Cat = namedtuple("Cat", "name age")
             expr = Cat("name", 5)._make(["newname", 6])""");

    doTest("Cat",
           """
             from collections import namedtuple
             Cat = namedtuple("Cat", "name age")
             expr = Cat._make(["newname", 6])""");

    doTest("Cat",
           """
             from collections import namedtuple
             class Cat(namedtuple("Cat", "name age")):
                 pass
             expr = Cat("name", 5)._make(["newname", 6])""");

    doTest("Cat",
           """
             from collections import namedtuple
             class Cat(namedtuple("Cat", "name age")):
                 pass
             expr = Cat._make(["newname", 6])""");
  }

  // PY-27148
  public void testTypingNTMake() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   """
                     from typing import NamedTuple
                     class Cat(NamedTuple):
                         name: str
                         age: int
                     expr = Cat("name", 5)._make(["newname", 6])""")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   """
                     from typing import NamedTuple
                     class Cat(NamedTuple):
                         name: str
                         age: int
                     expr = Cat._make(["newname", 6])""")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   """
                     from typing import NamedTuple
                     Cat = NamedTuple("Cat", name=str, age=int)
                     expr = Cat("name", 5)._make(["newname", 6])""")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   """
                     from typing import NamedTuple
                     Cat = NamedTuple("Cat", name=str, age=int)
                     expr = Cat._make(["newname", 6])""")
    );
  }

  // PY-27148
  public void testCollectionsNTReplace() {
    doTest("Cat",
           """
             from collections import namedtuple
             Cat = namedtuple("Cat", "name age")
             expr = Cat("name", 5)._replace(name="newname")""");

    doTest("Cat",
           """
             from collections import namedtuple
             class Cat(namedtuple("Cat", "name age")):
                 pass
             expr = Cat("name", 5)._replace(name="newname")""");

    doTest("str",
           """
             from collections import namedtuple
             Cat = namedtuple("Cat", "name age")
             expr = Cat("name", 5)._replace(age="five").age""");

    doTest("Cat",
           """
             from collections import namedtuple
             class Cat(namedtuple("Cat", "name age")):
                 pass
             expr = Cat._replace(Cat("name", 5), name="newname")""");
  }

  // PY-27148
  public void testTypingNTReplace() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   """
                     from typing import NamedTuple
                     class Cat(NamedTuple):
                         name: str
                         age: int
                     expr = Cat("name", 5)._replace(name="newname")""")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   """
                     from typing import NamedTuple
                     Cat = NamedTuple("Cat", name=str, age=int)
                     expr = Cat("name", 5)._replace(name="newname")""")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("int",
                   """
                     from typing import NamedTuple
                     Cat = NamedTuple("Cat", name=str, age=int)
                     expr = Cat("name", 5)._replace(age="give").age""")
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Cat",
                   """
                     from typing import NamedTuple
                     class Cat(NamedTuple):
                         name: str
                         age: int
                     expr = Cat._replace(Cat("name", 5), name="newname")""")
    );
  }

  // PY-21302
  public void testNewTypeReferenceTarget() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final PyExpression definition = parseExpr("""
                                                    from typing import NewType
                                                    UserId = NewType('UserId', int)
                                                    expr = UserId""");

        for (TypeEvalContext context : getTypeEvalContexts(definition)) {
          assertInstanceOf(context.getType(definition), PyTypingNewType.class);
        }

        final PyExpression instance = parseExpr("""
                                                  from typing import NewType
                                                  UserId = NewType('UserId', int)
                                                  expr = UserId(12)""");

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
                   """
                     from typing import NewType
                     UserId = NewType('UserId', int)
                     expr = UserId(12)""")
    );

    doTest("UserId",
           """
             from typing import NewType
             UserId = NewType(tp=int, name='UserId')
             expr = UserId(12)
             """);

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Type[UserId]",
                   """
                     from typing import Dict, NewType
                     UserId = NewType('UserId', Dict[int, str])
                     expr = UserId
                     """)
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("(a: UserId) -> str",
                   """
                     from typing import Dict, NewType
                     UserId = NewType('UserId', int)
                     def foo(a: UserId) -> str
                         pass
                     expr = foo
                     """)
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("UserId",
                   """
                     from typing import NewType as nt
                     UserId = nt('UserId', int)
                     expr = UserId(12)
                     """)
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("UserId",
                   """
                     import typing
                     UserId = typing.NewType('UserId', int)
                     expr = UserId(12)
                     """)
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("UserId",
                   """
                     import typing as t
                     UserId = t.NewType('UserId', int)
                     expr = UserId(12)
                     """)
    );

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("SuperId",
                   """
                     from typing import NewType
                     UserId = NewType('UserId', int)
                     SuperId = NewType('SuperId', UserId)
                     expr = SuperId(UserId(12))
                     """)
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
                   """
                     from typing_extensions import Protocol
                     from typing import TypeVar
                     T = TypeVar("T")
                     class MyProto1(Protocol[T]):
                         def func(self) -> T:
                             pass
                     class MyClass1(MyProto1[int]):
                         pass
                     expr = MyClass1().func()""")
    );
  }

  // PY-9634
  public void testAfterIsInstanceAndAttributeUsage() {
    doTest("Union[{bar}, int]",
           """
             def bar(y):
                 if isinstance(y, int):
                     pass
                 print(y.bar)    expr = y""");
  }

  // PY-28052
  public void testClassAttributeAnnotatedAsAny() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Any",
                   """
                     from typing import Any


                     class MyClass:
                         arbitrary: Any = 42


                     expr = MyClass().arbitrary""")
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
    doTest("TypeVar",
           "from typing import TypeVar\n" +
           "expr = TypeVar('T')");
  }

  // PY-28227
  public void testTypeVarTargetStub() {
    doMultiFileTest("TypeVar",
                    "from a import T\n" +
                    "expr = T");
  }

  // PY-29748
  public void testAfterIdentityComparison() {
    doTest("int",
           """
             a = 1
             if a is a:
                expr = a""");
  }

  // PY-31956
  public void testInAndNotBoolContains() {
    doTest("bool",
           """
             class MyClass:
                 def __contains__(self):
                     return 42

             expr = 1 in MyClass()""");
  }

  // PY-32533
  public void testSuperWithAnotherType() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doTest("A",
                   """
                     class A:
                         def f(self):
                             return 'A'

                     class B:
                         def f(self):
                             return 'B'

                     class C(B):
                         def f(self):
                             return 'C'

                     class D(C, A):
                         def f(self):
                             expr = super(B, self)
                             return expr.f()""")
    );
  }

  // PY-32113
  public void testAssertionOnVariableFromOuterScope() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("D",
                   """
                     class B: pass

                     class D(B): pass

                     g_b: B = undefined

                     def main() -> None:
                         assert isinstance(g_b, D)
                         expr = g_b""")
    );
  }

  // PY-32113
  public void testAssertionFunctionFromOuterScope() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("B",
                   """
                     class B: pass

                     def g_b():
                         pass

                     def main() -> None:
                         assert isinstance(g_b, B)
                         expr = g_b""")
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

        doTest("Literal[5]",
               "from typing_extensions import Final\n" +
               "expr: Final = 5");

        doTest("int",
               "from typing_extensions import Final\n" +
               "expr: Final[int]");

        doTest("List[int]",
               """
                 from typing_extensions import Final
                 expr: Final = [1, 2]
                 """);
      }
    );

    doTest("int",
           "from typing_extensions import Final\n" +
           "expr = undefined  # type: Final[int]");

    doTest("Literal[5]",
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
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTest("Literal[A.V1]",
                            """
                              from typing_extensions import Literal

                              from enum import Enum

                              class A(Enum):
                                  V1 = 1
                                  V2 = 2

                              expr: Literal[A.V1] = undefined""")
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
           """
             from typing_extensions import Literal
             a = Literal[1]
             b = Literal[2, 3]
             c = Literal[4, 5]
             d = Literal[b, c]
             expr = undefined  # type: Literal[a, d]""");

    doTest("Union[Literal[1, 2, \"foo\", 5], None]",
           "from typing_extensions import Literal\n" +
           "expr = undefined  # type: Literal[Literal[Literal[1, 2], \"foo\"], 5, None]");
  }

  // PY-40838
  public void testUnionOfManyTypesInclLiterals() {
    doTest("Literal[\"1\"]",
           """
             from typing import overload, Literal

             @overload
             def foo1() -> Literal["1"]:
                 pass

             @overload
             def foo1() -> Literal[2]:
                 pass

             @overload
             def foo1() -> bool:
                 pass

             @overload
             def foo1() -> None:
                 pass

             def foo1()
                 pass

             expr = foo1()""");
  }

  // PY-35235
  public void testOverloadsWithTypingLiteral() {
    final String prefix = """
      from typing_extensions import Literal
      from typing import overload

      @overload
      def foo(p1: Literal["a"]) -> str: ...

      @overload
      def foo(p1: Literal["b"]) -> bytes: ...

      @overload
      def foo(p1: str) -> int: ...

      def foo(p1):
          pass

      """;

    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        doTest("str",
               prefix +
               "a: Literal[\"a\"]\n" +
               "expr = foo(a)");

        doTest("int",
               prefix +
               "a = \"a\"\n" +
               "expr = foo(a)");

        doTest("str",
               prefix +
               "expr = foo(\"a\")");
      }
    );
  }

  // PY-33651
  public void testSlicingHomogeneousTuple() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("tuple[int, ...]",
                   """
                     from typing import Tuple
                     x: Tuple[int, ...]
                     expr = x[0:]""")
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
               """
                 from typing import TypedDict
                 class A(TypedDict):
                     x: int
                 a: A = {'x': 42}
                 expr = a""");
      }
    );
  }

  // PY-33663
  public void testAnnotatedSelfReturnProperty() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("A",
                   """
                     from typing import TypeVar

                     T = TypeVar("T")

                     class A:
                         @property
                         def foo(self: T) -> T:
                             pass

                     expr = A().foo""")
    );
  }

  // PY-30861
  public void testDontReplaceSpecifiedReturnTypeWithSelf() {
    doTest("dict",
           """
             from collections import defaultdict
             data = defaultdict(dict)
             expr = data['name']""");
  }

  // PY-37601
  public void testClassWithOwnInitInheritsClassWithGenericCall() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("Derived",
                   """
                     from typing import Any, Generic, TypeVar

                     T = TypeVar("T")

                     class Base(Generic[T]):
                         def __call__(self, p: Any) -> T:
                             pass

                     class Derived(Base):
                         def __init__():
                             pass

                     expr = Derived()""")
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpression() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int",
               """
                 from typing import TypedDict
                 class A(TypedDict):
                     x: int
                 a: A = {'x': 42}
                 expr = a['x']""");
        doTest("str",
               """
                 from typing import Literal, TypedDict
                 class TD(TypedDict):
                     a: int
                     b: str
                 def foo(v: TD, k: Literal['b']):
                     expr = v[k]""");
        doTest("bool | str",
               """
                 from typing import Literal, TypedDict
                 class TD(TypedDict):
                     a: int
                     b: str
                     c: bool
                 def foo(v: TD, k: Literal['c', 'b']):
                     expr = v[k]""");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionUndefinedKey() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("Any",
               """
                 from typing import TypedDict
                 class A(TypedDict):
                     x: int
                 a: A = {'x': 42}
                 expr = a[x]""");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionRequiredKey() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int",
               """
                 from typing import TypedDict
                 class A(TypedDict):
                     x: int
                 a: A = {'x': 42}
                 expr = a.get('x')""");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionOptionalKey() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int | None",
               """
                 from typing import TypedDict
                 class A(TypedDict, total=False):
                     x: int
                 a: A = {'x': 42}
                 expr = a.get('x')""");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionSameValueTypeAndDefaultArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int",
               """
                 from typing import TypedDict
                 class A(TypedDict, total=False):
                     x: int
                 a: A = {'x': 42}
                 expr = a.get('x', 42)""");
      }
    );
  }

  // PY-36008
  public void testTypedDictSubscriptionExpressionDifferentValueTypeAndDefaultArgument() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("int | str",
               """
                 from typing import TypedDict
                 class A(TypedDict, total=False):
                     x: int
                 a: A = {'x': 42}
                 expr = a.get('x', '')""");
      }
    );
  }

  // PY-36008
  public void testTypedDictAlternativeSyntax() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        doTest("A",
               """
                 from typing import TypedDict
                 A = TypedDict('A', {'x': int}, total=False)
                 expr = A""");
      }
    );
  }

  // PY-35881
  public void testResolveToAnotherFileClassWithBuiltinNameField() {
    doMultiFileTest(
      "int",
      """
        from foo import Foo
        foo = Foo(0)
        expr = foo.id"""
    );
  }

  // PY-35885
  public void testFunctionDunderDoc() {
    doTest("str",
           """
             def example():
                 ""\"Example Docstring""\"
                 return 0
             expr = example.__doc__""");
  }

  // PY-38786
  public void testParticularTypeAgainstTypeVarBoundedWithBuiltinType() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("type[MyClass]",
                   """
                     from typing import TypeVar, Type

                     T = TypeVar("T", bound=type)

                     def foo(t: T) -> T:
                         pass

                     class MyClass:
                         pass

                     expr = foo(MyClass)""")
    );
  }

  // PY-38786
  public void testDunderSubclasses() {
    doTest("List[Type[Base]]",
           """
             class Base(object):
                 pass
             expr = Base.__subclasses__()""");
  }

  // PY-37876
  public void testCallableParameterTypeVarMatching() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("int",
                   """
                     from typing import Callable, TypeVar, Any

                     T = TypeVar('T')
                     def func(x: Callable[[T], Any]) -> T:
                         pass

                     def callback(x: int) -> Any:
                         pass


                     expr = func(callback)""")
    );
  }

  // PY-37876
  public void testCallableParameterGenericTypeParameterMatching() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("int",
                   """
                     from typing import Callable, TypeVar, Any, List

                     T = TypeVar('T')


                     def func(f: Callable[[List[T]], Any]) -> T:
                         pass


                     def accepts_list_of_int(x: List[int]) -> Any:
                         pass


                     expr = func(accepts_list_of_int)
                     """)
    );
  }

  // PY-44470
  public void testInferringAndMatchingCls() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("Subclass",
                   """
                     class Subclass(dict):
                         def __new__(cls, *args, **kwargs):
                             expr = super().__new__(cls, *args, **kwargs)
                             return expr""")
    );
  }

  public void testFunctionReturnGeneric() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("(Any, str, T3) -> T3",
                   """
                     from typing import Callable, TypeVar

                     T1 = TypeVar('T1')
                     T2 = TypeVar('T2')
                     T3 = TypeVar('T3')

                     def bar(p1: T1, p2: T2) -> Callable[[T1, T2, T3], T3]:
                       pass

                     expr = bar(dunno, 'sd')""")
    );
  }

  // PY-54503
  public void testEnumGetItemResultValueAttribute() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("int",
                   """
                     import enum

                     class MyEnum(enum.Enum):
                         ONE = 1
                         TWO = 2

                     expr = MyEnum['ONE'].value""")
    );
  }

  // PY-54503
  public void testEnumDunderCallResultValueAttribute() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("int",
                   """
                     import enum

                     class MyEnum(enum.Enum):
                         ONE = 1
                         TWO = 2

                     expr = MyEnum(1).value""")
    );
  }

  // PY-54503
  public void testTypeHintedEnumItemValueAttribute() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTest("int",
                   """
                     import enum

                     class MyEnum(enum.Enum):
                         ONE = 1
                         TWO = 2

                     def f(p: MyEnum):
                         expr = p.value""")
    );
  }

  // PY-54503
  public void testImportedEnumGetItemResultValueAttribute() {
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + getTestName(false), "");
    @Nullable PyExpression expr = parseExpr("""
                                              from mod import MyEnum

                                              expr = MyEnum['ONE'].value""");
    assertNotNull(expr);
    TypeEvalContext codeAnalysisContext = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    assertType("Any", expr, codeAnalysisContext);
    assertProjectFilesNotParsed(codeAnalysisContext);

    TypeEvalContext userInitiatedContext = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile());
    assertType("int", expr, userInitiatedContext);
    assertProjectFilesNotParsed(userInitiatedContext);
  }

  public void testNonTrivialGenericArgumentTypeInDocstring() {
    doTest("Iterator[int]",
           """
             def f1(xs):
                 ""\"
                 :type xs: collections.Iterable of T
                 ""\"
                 return iter(xs)

             expr = f1([1, 2, 3])
             """);
  }

  public void testGenericClassTypeVarFromDocstrings() {
    doTest("int",
           """
             class User1(object):
                 def __init__(self, x):
                     ""\"
                     :type x: T
                     :rtype: User1 of T
                     ""\"
                     self.x = x

                 def get(self):
                     ""\"
                     :rtype: T
                     ""\"
                     return self.x

             c = User1(10)
             expr = c.get()""");
  }

  // PY-28076
  public void testAssignmentParens() {
    doTest("int", "((expr)) = 42");
  }

  public void testElif1() {
    doTest("str",
           """
            class A:
                pass
            
            def foo(a: int | str | A):
                if isinstance(a, A):
                    pass
                elif isinstance(a, int):
                    pass
                else:
                    expr = a
            """);
  }

  public void testElif2() {
    doTest("A",
           """
            class A:
                pass
            
            def foo(a: int | str | A):
               if isinstance(a, int):
                   pass
               elif not isinstance(a, str):
                   expr = a
            """);
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
