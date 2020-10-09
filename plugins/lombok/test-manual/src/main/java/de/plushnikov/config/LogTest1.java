package de.plushnikov.config;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogTest1 {

  public void logSomething() {
    LOG2.info("Hello World!");
  }

  public static void main(String[] args) {
    LOG2.info("Test");
    new LogTest1().logSomething();
  }
}
