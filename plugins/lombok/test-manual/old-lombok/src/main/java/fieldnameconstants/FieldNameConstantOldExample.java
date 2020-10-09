package fieldnameconstants;

import lombok.AccessLevel;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(suffix = "_SUF")
public class FieldNameConstantOldExample {
  private String stringField;
  private int someInteger;

  @FieldNameConstants(level = AccessLevel.PRIVATE)
  private int someFloat;

  public static void main(String[] args) {
    System.out.println(FIELD_SOME_FLOAT_NAME);
    System.out.println(FIELD_SOME_INTEGER_SUF);
    System.out.println(FIELD_STRING_FIELD_SUF);
  }
}
