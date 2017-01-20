package de.plushnikov.constructor;

import lombok.Data;

public class DataFieldDefaultsTestIssue162 {

  @Data
  public static class A {
    final int i;
    final String j;
  }

  public static class B extends A {
    public B() {
      super();
    }

    public B(int i, String j) {
      super(i, j);
    }
  }
}
