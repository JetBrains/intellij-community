package com.jetbrains.python.psi.types;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class TypeEvalContext {
  private final boolean myAllowDataFlow;
  private final boolean myAllowStubToAST;

  private final Map<PyExpression, PyType> myEvaluated = new HashMap<PyExpression, PyType>();

  private TypeEvalContext(boolean allowDataFlow, boolean allowStubToAST) {
    myAllowDataFlow = allowDataFlow;
    myAllowStubToAST = allowStubToAST;
  }

  public boolean allowDataFlow() {
    return myAllowDataFlow;
  }

  public boolean allowReturnTypes() {
    return myAllowDataFlow;
  }

  public boolean allowStubToAST() {
    return myAllowStubToAST;
  }

  public static TypeEvalContext slow() {
    return new TypeEvalContext(true, true);
  }

  public static TypeEvalContext fast() {
    return new TypeEvalContext(false, true);
  }

  public static TypeEvalContext fastStubOnly() {
    return new TypeEvalContext(false, false);
  }

  @Nullable
  public PyType getType(@NotNull PyExpression expr) {
    synchronized (myEvaluated) {
      if (myEvaluated.containsKey(expr)) {
        return myEvaluated.get(expr);
      }
    }
    PyType result = expr.getType(this);
    synchronized (myEvaluated) {
      myEvaluated.put(expr, result);
    }
    return result;
  }

  public boolean maySwitchToAST(StubBasedPsiElement element) {
    return myAllowStubToAST || element.getStub() == null;
  }
}
