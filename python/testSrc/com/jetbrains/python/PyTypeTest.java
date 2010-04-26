package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeReference;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.io.IOException;

/**
 * @author yole
 */
public class PyTypeTest extends PyLightFixtureTestCase {
  public void testTupleType() {
    PyClassType type = (PyClassType) doTest("t = ('a', 2)\nexpr = t[0]");
    assertEquals("str", type.getName());
  }

  public void testBinaryExprType() {
    PyClassType type = (PyClassType) doTest("expr = 1 + 2");
    assertEquals("int", type.getName());

    type = (PyClassType) doTest("expr = '1' + '2'");
    assertEquals("str", type.getName());

    type = (PyClassType) doTest("expr = '%s' % ('a')");
    assertEquals("str", type.getName());
  }

  public void testUnaryExprType() {
    PyClassType type = (PyClassType) doTest("expr = -1");
    assertEquals("int", type.getName());
  }

  public void testTypeFromComment() {
    PyClassType type = (PyClassType) doTest("expr = ''.capitalize()");
    assertEquals("str", type.getName());
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
    PyType type = expr.getType(TypeEvalContext.slow());
    while(type instanceof PyTypeReference) {
      type = ((PyTypeReference) type).resolve(expr);
    }
    return type;
  }
}
  
