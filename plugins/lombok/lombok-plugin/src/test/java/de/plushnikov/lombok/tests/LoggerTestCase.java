package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

public class LoggerTestCase extends LombokParsingTestCase {
  public void testLoggerCommons() throws IOException {
    doTest();
  }

  public void testLoggerJul() throws IOException {
    doTest();
  }

  public void testLoggerLog4j() throws IOException {
    doTest();
  }

  public void testLoggerLog4j2() throws IOException {
    doTest();
  }

  public void testLoggerSlf4j() throws IOException {
    doTest();
  }

  public void testLoggerSlf4jAlreadyExists() throws IOException {
    doTest();
  }

  public void testLoggerSlf4jOnNonType() throws IOException {
    doTest();
  }

  public void testLoggerSlf4jTypes() throws IOException {
    doTest();
  }

  public void testLoggerSlf4jWithPackage() throws IOException {
    doTest();
  }

  public void testLoggerXSlf4j() throws IOException {
    doTest();
  }
}