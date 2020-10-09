package de.plushnikov.fieldnameconstants;

import lombok.experimental.FieldNameConstants;

@FieldNameConstants
public class FieldNameConstantOldExample {
  private String stringField;
  private int someInteger;

  public static void main(String[] args) {
    System.out.println(Fields.someInteger);
    System.out.println(Fields.stringField);
  }
}
