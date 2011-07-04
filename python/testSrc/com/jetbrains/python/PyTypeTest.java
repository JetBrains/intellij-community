package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.*;

/**
 * @author yole
 */
public class PyTypeTest extends PyLightFixtureTestCase {
  public void testTupleType() {
    PyClassType type = (PyClassType) doTest("t = ('a', 2)\nexpr = t[0]");
    assertEquals("str", type.getName());
  }

  public void testTupleAssignmentType() {
    PyClassType type = (PyClassType) doTest("t = ('a', 2)\n(expr, q) = t");
    assertEquals("str", type.getName());
  }

  public void testBinaryExprType() {
    PyClassType type = (PyClassType) doTest("expr = 1 + 2");
    assertEquals("int", type.getName());

    type = (PyClassType) doTest("expr = '1' + '2'");
    assertEquals("str", type.getName());

    type = (PyClassType) doTest("expr = '%s' % ('a')");
    assertEquals("str", type.getName());

    type = (PyClassType) doTest("expr = [1] + [2]");
    assertEquals("list", type.getName());
  }

  public void testUnaryExprType() {
    PyClassType type = (PyClassType) doTest("expr = -1");
    assertEquals("int", type.getName());
  }

  public void testTypeFromComment() {
    PyClassType type = (PyClassType) doTest("expr = ''.capitalize()");
    assertEquals("str", type.getName());
  }

  public void testUnionOfTuples() {
    PyTupleType type = (PyTupleType) doTest("def x():\n" +
                                            "  if True:\n" +
                                            "    return (1, 'a')\n" +
                                            "  else:\n" +
                                            "    return ('a', 1)\n" +
                                            "expr = x()");
    assertTrue(type.getElementType(0) instanceof PyUnionType);
    assertTrue(type.getElementType(1) instanceof PyUnionType);
  }

  public void testAugAssignment() {
    PyClassType type = (PyClassType) doTest("def x():\n" +
                                            "    count = 0\n" +
                                            "    count += 1\n" +
                                            "    return count\n" +
                                            "expr = x()");
    assertEquals("int", type.getName());
  }

  public void testSetComp() {
    PyClassType type = (PyClassType) doTest("expr = {i for i in range(3)}");
    assertEquals("set", type.getName());
  }

  public void testSet() {
    PyClassType type = (PyClassType) doTest("expr = {1, 2, 3}");
    assertEquals("set", type.getName());
    assertInstanceOf(type, PyCollectionType.class);
    final PyType elementType = ((PyCollectionType)type).getElementType(TypeEvalContext.fast());
    assertEquals("int", elementType.getName());
  }

  public void testNone() {   // PY-1425
    PyType type = doTest("class C:\n" +
                         "    def __init__(self): self.foo = None\n" +
                         "expr = C().foo");
    assertNull(type);
  }

  public void testUnicodeLiteral() {  // PY-1427
    PyClassType type = (PyClassType) doTest("expr = u'foo'");
    assertEquals("unicode", type.getName());
  }

  // TODO uncomment when we have a mock SDK for Python 3.x
  public void _testBytesLiteral() {  // PY-1427
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      PyClassType type = (PyClassType) doTest("expr = b'foo'");
      assertEquals("bytes", type.getName());
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testPropertyType() {
    PyType type = doTest("class C:\n" +
                         "    x = property(lambda self: object(), None, None)\n" +
                         "expr = C.x");
    assertNull(type);
  }

  public void testIterationType() {
    PyClassType type = (PyClassType) doTest("for expr in [1, 2, 3]: pass");
    assertEquals("int", type.getName());
  }

  public void testSubscriptType() {
    PyClassType type = (PyClassType) doTest("l = [1, 2, 3]; expr = l[0]");
    assertEquals("int", type.getName());
  }

  public void testSliceType() {
    PyCollectionType type = (PyCollectionType) doTest("l = [1,2,3]; expr=l[0:1]");
    assertEquals("int", type.getElementType(TypeEvalContext.fast()).getName());
  }

  public void testExceptType() {
    PyClassType type = (PyClassType) doTest("try:\n" +
                                            "    pass\n" +
                                            "except ImportError, expr:\n" +
                                            "    pass");
    assertEquals("ImportError", type.getName());
  }

  public void testTypeAnno() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      PyClassType type = (PyClassType) doTest("def foo(x: str) -> list: expr = x");
      assertEquals("str", type.getName());
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testReturnTypeAnno() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      PyClassType type = (PyClassType) doTest("def foo(x) -> list: return x\n" +
                                              "expr = foo(None)");
      assertEquals("list", type.getName());
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testEpydocReturnType() {
    PyClassType type = (PyClassType) doTest("def foo(*args):\n" +
                                            "    '''@rtype: C{str}'''\n" +
                                            "    return args[0]" +
                                            "expr = foo('')");
    assertEquals("str", type.getName());
  }

  public void testEpydocParamType() {
    PyClassType type = (PyClassType) doTest("def foo(s):\n" +
                                            "    '''@type s: C{str}'''\n" +
                                            "    expr = s");
    assertEquals("str", type.getName());
  }

  public void testEpydocIvarType() {
    PyClassType type = (PyClassType) doTest("class C:\n" +
                                            "    s = None\n" +
                                            "    '''@type: C{int}'''\n" +
                                            "    def foo(self):\n" +
                                            "        expr = self.s");
    assertEquals("int", type.getName());
  }

  public void testRestParamType() {
    PyClassType type = (PyClassType) doTest("def foo(limit):\n" +
                                            "  ''':param integer limit: maximum number of stack frames to show'''\n" +
                                            "  expr = limit");
    assertEquals("int", type.getName());
  }

  public void testRestClassType() {     //PY-3849
    PyClassType type = (PyClassType) doTest("class Foo: pass\ndef foo(limit):\n" +
                                            "  ''':param :class:`Foo` limit: maximum number of stack frames to show'''\n" +
                                            "  expr = limit");
    assertEquals("Foo", type.getName());
  }

  public void testRestIvarType() {
    PyClassType type = (PyClassType) doTest("def foo(p):\n" +
                                            "    var = p.bar\n" +
                                            "    ''':type var: str'''\n" +
                                            "    expr = var");
    assertEquals("str", type.getName());
  }

  private PyType doTest(final String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    return expr.getType(TypeEvalContext.slow());
  }
}
  
