package de.plushnikov.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class DataTest {
  @Data
  class A {
    int aaa;
    String bbbb;
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  class B extends A {
    float ffff;
    char ccc;
  }

  class DataOnLocalClass1 {
    public void main(String[] args) {
      @Data
      class Local {
        final int x;
        String name;
      }

      Local local = new Local(2);
      local.getName();
    }
  }
}
