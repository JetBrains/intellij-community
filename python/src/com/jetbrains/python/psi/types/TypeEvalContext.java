package com.jetbrains.python.psi.types;

/**
 * @author yole
 */
public class TypeEvalContext {
  private boolean myAllowDataFlow;

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
}
