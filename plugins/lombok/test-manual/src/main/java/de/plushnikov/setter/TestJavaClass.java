package de.plushnikov.setter;

import lombok.Getter;
import lombok.Setter;

public class TestJavaClass {

  @Setter
  @Getter
  private String field;

  void aFunction() {
    if (this.field == null) {
      System.out.println("sss");
    }
  }
}
