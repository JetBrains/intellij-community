package de.plushnikov.data;

import lombok.Data;

public class DataFieldDefaultsTest {

  @Data
  public static class A {
    final int i;
    final String j;
  }

  public static class B extends A {
    //TODO fix it
    //public B() {
    //    super();                    // ERROR (cannot be applied to given types) but IDEA say all is okay
    //}

    public B(int i, String j) {
      super(i, j);
    }
  }
}