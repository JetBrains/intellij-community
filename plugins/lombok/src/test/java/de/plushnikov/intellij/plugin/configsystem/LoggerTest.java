package de.plushnikov.intellij.plugin.configsystem;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class LoggerTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/log";
  }

  public void testFieldIsStatic$LogTest() {
    doTest();
  }

  public void testFieldIsStatic$Log4jTest() {
    doTest();
  }

  public void testFieldIsStatic$Log4j2Test() {
    doTest();
  }

  public void testFieldIsStatic$CommonsLogTest() {
    doTest();
  }

  public void testFieldIsStatic$Slf4jTest() {
    doTest();
  }

  public void testFieldIsStatic$XSlf4jTest() {
    doTest();
  }

  public void testFieldIsStatic$JBossLogTest() {
    doTest();
  }

  public void testFieldIsStatic$Slf4jWithReqConstructor() {
    doTest();
  }

  public void testFieldName$LogTest() {
    doTest();
  }

  public void testFieldName$Log4jTest() {
    doTest();
  }

  public void testFieldName$Log4j2Test() {
    doTest();
  }

  public void testFieldName$CommonsLogTest() {
    doTest();
  }

  public void testFieldName$Slf4jTest() {
    doTest();
  }

  public void testFieldName$XSlf4jTest() {
    doTest();
  }

  public void testFieldName$JBossLogTest() {
    doTest();
  }

  public void testCustomSimple$CustomLogTest() {
    doTest();
  }

  public void testCustomComplex$CustomLogTest() {
    doTest();
  }
}
