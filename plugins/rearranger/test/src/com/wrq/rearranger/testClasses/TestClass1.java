package com.wrq.rearranger.testClasses;

/**
 * Created by IntelliJ IDEA.
 * User: davek
 * Date: Oct 30, 2003
 * Time: 2:53:43 PM
 * To change this template use Options | File Templates.
 */
final class TestClass1 {
  public static final int CONSTANT1 = 1;
  public static final int CONSTANT2 = 2;
  /** end of public static final fields */
  private final int privateField1;

  public TestClass1(final int privateField1) {
    this.privateField1 = privateField1;
  }
}