package com.jetbrains.python.psi.resolve;

import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyResolveContext {
  private final boolean myAllowImplicits;
  private final TypeEvalContext myTypeEvalContext;

  private PyResolveContext(boolean allowImplicits) {
    myAllowImplicits = allowImplicits;
    myTypeEvalContext = null;
  }

  private PyResolveContext(boolean allowImplicits, TypeEvalContext typeEvalContext) {
    myAllowImplicits = allowImplicits;
    myTypeEvalContext = typeEvalContext;
  }

  public boolean allowImplicits() {
    return myAllowImplicits;
  }

  private static final PyResolveContext ourDefaultContext = new PyResolveContext(true);
  private static final PyResolveContext ourNoImplicitsContext = new PyResolveContext(false);

  public static PyResolveContext defaultContext() {
    return ourDefaultContext;
  }

  public static PyResolveContext noImplicits() {
    return ourNoImplicitsContext;
  }

  public PyResolveContext withTypeEvalContext(TypeEvalContext context) {
    return new PyResolveContext(myAllowImplicits, context);
  }

  public TypeEvalContext getTypeEvalContext() {
    return myTypeEvalContext != null ? myTypeEvalContext : TypeEvalContext.fast();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyResolveContext that = (PyResolveContext)o;

    if (myAllowImplicits != that.myAllowImplicits) return false;
    if (myTypeEvalContext != null ? !myTypeEvalContext.equals(that.myTypeEvalContext) : that.myTypeEvalContext != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAllowImplicits ? 1 : 0);
    result = 31 * result + (myTypeEvalContext != null ? myTypeEvalContext.hashCode() : 0);
    return result;
  }
}
