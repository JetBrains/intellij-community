package de.plushnikov.log;

import lombok.extern.apachecommons.CommonsLog;

@CommonsLog
public class CommonsLogClass {

  private int intProperty;

  private float floatProperty;

  private String stringProperty;

  public void doSomething() {
    log.error("Error Message Text");
  }
}
