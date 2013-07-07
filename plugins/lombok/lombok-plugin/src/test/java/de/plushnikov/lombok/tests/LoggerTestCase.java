package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

import java.io.IOException;

public class LoggerTestCase extends LombokParsingTestCase {
  public LoggerTestCase() {
  }

  @Test
  public void testLoggerCommons()throws IOException {
    doTest();
  }

  @Test
  public void testLoggerJul() throws IOException {
    doTest();
  }

  @Test
  public void testLoggerLog4j() throws IOException {
    doTest();
  }

  @Test
  public void testLoggerSlf4j() throws IOException {
    doTest();
  }

  @Test
  public void testLoggerSlf4jAlreadyExists()throws IOException  {
    doTest();
  }

  @Test
  public void testLoggerSlf4jOnNonType()throws IOException  {
    doTest();
  }

  @Test
  public void testLoggerSlf4jTypes() throws IOException {
    doTest();
  }

  @Test
  public void testLoggerSlf4jWithPackage() throws IOException {
    doTest();
  }
}