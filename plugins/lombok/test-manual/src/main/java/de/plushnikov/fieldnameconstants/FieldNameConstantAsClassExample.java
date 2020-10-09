package de.plushnikov.fieldnameconstants;

import lombok.experimental.FieldNameConstants;

@FieldNameConstants
public class FieldNameConstantAsClassExample {
  private String stringField;
  private int someInteger;
  @FieldNameConstants.Exclude
  private float excluded;

  public static void main(String[] args) {
    System.out.println(Fields.someInteger);
    System.out.println(Fields.stringField);
    System.out.println(FieldNameConstantAsClassExample.Fields.stringField);
  }
}
