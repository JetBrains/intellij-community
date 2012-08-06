package com.jetbrains.python.psi.types;

/**
 * @author traff
 */
public class PyWeakNoneType extends PyNoneType implements PyWeakType{
  public static PyWeakNoneType INSTANCE = new PyWeakNoneType();

  private PyWeakNoneType() {
  }
}
