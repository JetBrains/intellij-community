package de.plushnikov.log;

import lombok.extern.slf4j.XSlf4j;

@XSlf4j
public class XSlf4jClass {

  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  public void doSomething() {
    log.info("Information message text {}", log.getName());
  }

  public static void main(String[] args) {
    new XSlf4jClass().doSomething();
  }
}
