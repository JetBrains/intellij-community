package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.*;

import java.io.IOException;

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

  private PyType doTest(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          myFixture.configureByText(PythonFileType.INSTANCE,
                                    text);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    return expr.getType(TypeEvalContext.slow());
  }
}
  
