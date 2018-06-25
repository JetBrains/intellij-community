package de.plushnikov.log;

import lombok.extern.flogger.Flogger;

@Flogger
public class FloggerClass {
  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  public void doSomething() {
    log.withWarn.log("Warning message text");
    log.withError.log("Error message text");
  }

  public static void main(String[] args) {
    new FloggerClass().doSomething();
  }
}
