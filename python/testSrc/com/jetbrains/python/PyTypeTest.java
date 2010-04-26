package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.io.IOException;

/**
 * @author yole
 */
public class PyTypeTest extends PyLightFixtureTestCase {
  public void testTupleType() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          myFixture.configureByText(PythonFileType.INSTANCE,
                                    "t = ('a', 2)\nexpr = t[0]");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    PyClassType type = (PyClassType) expr.getType(TypeEvalContext.slow());
    assertEquals("str", type.getName());
  }
}
  
