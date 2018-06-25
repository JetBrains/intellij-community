package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class LoggerTest extends AbstractLombokParsingTestCase {

  public void testLogger$LoggerCommons() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerJul() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerLog4j() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerLog4j2() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerSlf4j() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jAlreadyExists() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jOnNonType() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jTypes() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jWithPackage() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerXSlf4j() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerJBossLog() throws IOException {
    doTest(true);
  }

  public void testLogger$LoggerFlogger() throws IOException {
    doTest(true);
  }
}
