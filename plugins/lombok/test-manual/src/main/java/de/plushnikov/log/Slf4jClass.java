package de.plushnikov.log;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Slf4jClass {
  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  public void doSomething() {
    log.info("Information message text");
    log.getName();
  }
}
