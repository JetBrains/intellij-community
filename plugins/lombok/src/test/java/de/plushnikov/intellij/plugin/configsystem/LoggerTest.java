package de.plushnikov.intellij.plugin.configsystem;

import de.plushnikov.intellij.plugin.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class LoggerTest extends LombokParsingTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/log";
  }

  public void doTest() throws IOException {
    final String fullFileName = getTestName(true).replace('$', '/') + ".java";
    final String subPath = fullFileName.substring(0, fullFileName.lastIndexOf('/'));
    final String fileName = fullFileName.substring(fullFileName.lastIndexOf('/') + 1);

    myFixture.copyFileToProject(getBasePath() + "/" + subPath + "/lombok.config", "lombok.config");

    doTest(fullFileName, subPath + "/after/" + fileName);
  }

  public void testFieldIsStatic$LogTest() throws IOException {
    doTest();
  }

  public void testFieldIsStatic$Log4jTest() throws IOException {
    doTest();
  }

  public void testFieldIsStatic$Log4j2Test() throws IOException {
    doTest();
  }

  public void testFieldIsStatic$CommonsLogTest() throws IOException {
    doTest();
  }

  public void testFieldIsStatic$Slf4jTest() throws IOException {
    doTest();
  }

  public void testFieldIsStatic$XSlf4jTest() throws IOException {
    doTest();
  }


  public void testFieldName$LogTest() throws IOException {
    doTest();
  }

  public void testFieldName$Log4jTest() throws IOException {
    doTest();
  }

  public void testFieldName$Log4j2Test() throws IOException {
    doTest();
  }

  public void testFieldName$CommonsLogTest() throws IOException {
    doTest();
  }

  public void testFieldName$Slf4jTest() throws IOException {
    doTest();
  }

  public void testFieldName$XSlf4jTest() throws IOException {
    doTest();
  }
}