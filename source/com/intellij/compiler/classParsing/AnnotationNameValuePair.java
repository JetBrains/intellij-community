package com.intellij.compiler.classParsing;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 8, 2004
 */
public class AnnotationNameValuePair {
  private final int myName;
  private final ConstantValue myValue;

  public AnnotationNameValuePair(int name, ConstantValue value) {
    myName = name;
    myValue = value;
  }

  public int getName() {
    return myName;
  }

  public ConstantValue getValue() {
    return myValue;
  }
}
