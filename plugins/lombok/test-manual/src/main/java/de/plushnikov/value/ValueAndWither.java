package de.plushnikov.value;

import lombok.Value;
import lombok.experimental.Wither;

@Wither
@Value
public class ValueAndWither {
  private String myField;

  public ValueAndWither methodCallingWith() {
//    myField = "this is not possible";
    return withMyField("xyz");
  }

  public static void main(String[] args) {
    ValueAndWither test = new ValueAndWither("abc");
    System.out.println(test);
    System.out.println(test.methodCallingWith());
  }
}
