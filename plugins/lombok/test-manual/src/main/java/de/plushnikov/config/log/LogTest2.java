package de.plushnikov.config.log;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogTest2 {

  public void logSomething() {
    LOGGER1.info("Hello World!");
  }

  public static void main(String[] args) {
    LOGGER1.info("Test");
    new LogTest2().logSomething();
  }
}
