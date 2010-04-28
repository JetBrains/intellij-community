package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class TypeEvalContext {
  private boolean myAllowDataFlow;
  private Map<PyExpression, PyType> myEvaluated = new HashMap<PyExpression, PyType>();

  private TypeEvalContext(boolean allowDataFlow) {
    myAllowDataFlow = allowDataFlow;
  }

  public boolean allowDataFlow() {
    return myAllowDataFlow;
  }

  public boolean allowReturnTypes() {
    return myAllowDataFlow;
  }

  public static TypeEvalContext slow() {
    return new TypeEvalContext(true);
  }

  public static TypeEvalContext fast() {
    return new TypeEvalContext(false);
  }

  @Nullable
  public PyType getType(PyExpression expr) {
    if (myEvaluated.containsKey(expr)) {
      return myEvaluated.get(expr);
    }
    PyType result = expr.getType(this);
    myEvaluated.put(expr, result);
    return result;
  }
}
