package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

public class LoggerTestCase extends LombokParsingTestCase {
  public LoggerTestCase() {
  }

  @Test
  public void testLoggerCommons() {
    doTest();
  }

  @Test
  public void testLoggerJul() {
    doTest();
  }

  @Test
  public void testLoggerLog4j() {
    doTest();
  }

  @Test
  public void testLoggerSlf4j() {
    doTest();
  }

  @Test
  public void testLoggerSlf4jAlreadyExists() {
    doTest();
  }

  @Test
  public void testLoggerSlf4jOnNonType() {
    doTest();
  }

  @Test
  public void testLoggerSlf4jTypes() {
    doTest();
  }

  @Test
  public void testLoggerSlf4jWithPackage() {
    doTest();
  }
}