package de.plushnikov.equalshashcode;

import lombok.EqualsAndHashCode;

import java.util.Date;

public final class FinalClassCanEqualTest {

  @EqualsAndHashCode
  final static class FinalObject {

  }

  @EqualsAndHashCode
  static class NonFinalObject {

  }

  @EqualsAndHashCode(callSuper = false)
  final static class FinalDate extends Date {

  }

  @EqualsAndHashCode(callSuper = false)
  static class NonFinalDate extends Date {

  }

  public static void main(String[] args) {
    FinalObject f1 = new FinalObject();
    //f1.canEqual("");

    NonFinalObject f2 = new NonFinalObject();
    f2.canEqual("");

    FinalDate f3 = new FinalDate();
    f3.canEqual("");

    NonFinalDate f4 = new NonFinalDate();
    f4.canEqual("");
  }
}
