package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class LoggerTest extends AbstractLombokParsingTestCase {

  public void testLogger$LoggerCommons() {
    doTest(true);
  }

  public void testLogger$LoggerJul() {
    doTest(true);
  }

  public void testLogger$LoggerLog4j() {
    doTest(true);
  }

  public void testLogger$LoggerLog4j2() {
    doTest(true);
  }

  public void testLogger$LoggerSlf4j() {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jAlreadyExists() {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jOnNonType() {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jTypes() {
    doTest(true);
  }

  public void testLogger$LoggerSlf4jWithPackage() {
    doTest(true);
  }

  public void testLogger$LoggerXSlf4j() {
    doTest(true);
  }

  public void testLogger$LoggerJBossLog() {
    doTest(true);
  }

  public void testLogger$LoggerFlogger() {
    doTest(true);
  }

  // we cannot test CustomLog here because it requires config
}
