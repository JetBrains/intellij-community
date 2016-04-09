package de.plushnikov.log;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggerRenameTest {

  public void doSomething() {
    log.debug("Some debug text");
    log.info("Some info text LoggerRenameTest");
  }

  public static void main(String[] args) {
    LoggerRenameTest test = new LoggerRenameTest();
    test.doSomething();
    LoggerRenameTest.log.warn("Warning");
    test.log.warn("Warning");
    log.error("error");
  }
}
