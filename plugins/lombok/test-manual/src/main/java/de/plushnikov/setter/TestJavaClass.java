package de.plushnikov.setter;

import lombok.Getter;
import lombok.Setter;

public class TestJavaClass {

  @Setter
  @Getter
  private String field;

  void aFunction() {
    String value = getField();
    if (field == null) {
      System.out.println("Field: " + value);
    }
  }
}
