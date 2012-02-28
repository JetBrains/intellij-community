package com.jetbrains.python;

import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.*;

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
    doTest("int or long or float or complex",
           "expr = 1 + 2");
    doTest("str or unicode",
           "expr = '1' + '2'");
    doTest("str or unicode",
           "expr = '%s' % ('a')");
    doTest("list",
           "expr = [1] + [2]");
  }

  public void testAssignmentChainBinaryExprType() {
    doTest("int or long or float or complex",
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
    doTest("(int or str, str or int)",
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
    doTest("set of int",
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
    doTest("unknown",
           "class C:\n" +
           "    x = property(lambda self: object(), None, None)\n" +
           "expr = C.x");
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
    doTest("list of int",
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
    final String text = "def f(c, x):\n" +
                        "    if c:\n" +
                        "        return 1\n" +
                        "    return x\n" +
                        "expr = f(1, 2)\n";
    PyExpression expr = parseExpr(text);
    PyType t = expr.getType(TypeEvalContext.slow());
    assertTrue(PyTypeChecker.isUnknown(t));
    doTest("int", text);
  }

  public void testReturnTypeReferenceEquality() {
    final String text = "def foo(x): return x.bar\n" +
                        "def xyzzy(a, x):\n" +
                        "    if a:\n" +
                        "        return foo(x)\n" +
                        "    else:\n" +
                        "        return foo(x)\n" +
                        "expr = xyzzy(a, b)";
    PyExpression expr = parseExpr(text);
    PyType t = expr.getType(TypeEvalContext.slow());
    assertInstanceOf(t, PyTypeReference.class);
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
    TypeEvalContext context = TypeEvalContext.slow().withTracing();
    PyType actual = expr.getType(context);
    assertFalse(actual.isBuiltin(context));
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
    TypeEvalContext context = TypeEvalContext.slow().withTracing();
    PyType actual = expr.getType(context);
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
    TypeEvalContext context = TypeEvalContext.slow().withTracing();
    PyType actual = expr.getType(context);
    assertNotNull(actual);
    assertEquals("int", actual.getName());
  }

  // PY-5831
  public void testYieldType() {
    PyExpression expr = parseExpr("def f():\n" +
                                  "    expr = yield 2\n");
    TypeEvalContext context = TypeEvalContext.slow().withTracing();
    PyType actual = expr.getType(context);
    assertNull(actual);
  }

  private PyExpression parseExpr(String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    return myFixture.findElementByText("expr", PyExpression.class);
  }

  private static String msg(PyType expected, PyType actual, TypeEvalContext context) {
    return String.format("Expected: %s, actual: %s",
                         PythonDocumentationProvider.getTypeName(expected, context),
                         PythonDocumentationProvider.getTypeName(actual, context));
  }

  private void doTest(final String expectedType, final String text) {
    PyExpression expr = parseExpr(text);
    TypeEvalContext context = TypeEvalContext.slow().withTracing();
    PyType actual = expr.getType(context);
    PyType expected = PyTypeParser.getTypeByName(expr, expectedType);
    if (expected != null) {
      assertNotNull(context.printTrace(), actual);
      assertTrue(msg(expected, actual, context), PyTypeChecker.match(expected, actual, context));
    }
  }
}
