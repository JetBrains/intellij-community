package com.jetbrains.python.psi.resolve;

/**
 * @author yole
 */
public class PyResolveContext {
  private final boolean myAllowImplicits;

  private PyResolveContext(boolean allowImplicits) {
    myAllowImplicits = allowImplicits;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyResolveContext that = (PyResolveContext)o;

    if (myAllowImplicits != that.myAllowImplicits) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return (myAllowImplicits ? 1 : 0);
  }
}
