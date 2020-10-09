package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokLoggerActionTest extends LombokLightActionTestCase {

  protected AnAction getAction() {
    return new DelombokLoggerAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/log";
  }

  public void testLog() throws Exception {
    doTest();
  }

  public void testLog4j() throws Exception {
    doTest();
  }

  public void testLog4j2() throws Exception {
    doTest();
  }

  public void testCommonsLog() throws Exception {
    doTest();
  }

  public void testSlf4j() throws Exception {
    doTest();
  }

  public void testXSlf4j() throws Exception {
    doTest();
  }

  public void testJBossLog() throws Exception {
    doTest();
  }

  public void testFlogger() throws Exception {
    doTest();
  }
}
