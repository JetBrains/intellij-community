package de.plushnikov.log;

import lombok.extern.log4j.Log4j;

@Log4j
public class Log4jClass {
  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  public void doSomething() {
    log.warn("Warning message text");
    log.error("Error message text");
  }

  public static void main(String[] args) {
    new Log4jClass().doSomething();
  }
}
