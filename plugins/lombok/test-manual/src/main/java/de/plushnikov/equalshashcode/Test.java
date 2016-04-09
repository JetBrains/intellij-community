package de.plushnikov.equalshashcode;

import lombok.EqualsAndHashCode;

public class Test {
//    @EqualsAndHashCode(callSuper = false)
//    class EqualsAndHashCodeWithExistingMethods extends Object {
//        int x;
//    }

  @EqualsAndHashCode
  class EqualsAndHashCodeWithExistingMethods {
    int x;

    public int hashCode() {
      return 42;
    }
  }

  @EqualsAndHashCode
  final class EqualsAndHashCodeWithExistingMethods2 {
    int x;

    public boolean equals(Object other) {
      return false;
    }
  }

  @EqualsAndHashCode(callSuper = false)
  final class EqualsAndHashCodeWithExistingMethods3 extends EqualsAndHashCodeWithExistingMethods {
    int x;

    protected boolean canEqual(Object other) {
      return true;
    }
  }
}
