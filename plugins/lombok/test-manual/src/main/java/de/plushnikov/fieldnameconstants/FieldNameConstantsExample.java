package de.plushnikov.fieldnameconstants;

import lombok.experimental.FieldNameConstants;
import lombok.AccessLevel;

@FieldNameConstants
public class FieldNameConstantsExample {
  private String iAmAField;

  @FieldNameConstants(level = AccessLevel.MODULE)
  private int andSoAmI;

  public static void main(String[] args) {
    System.out.println(FieldNameConstantsExample.MYPREFIX_AND_SO_AM_I_MYSUFFIX);
    System.out.println(FieldNameConstantsExample.MYPREFIX_I_AM_A_FIELD_MYSUFFIX);
  }
}
