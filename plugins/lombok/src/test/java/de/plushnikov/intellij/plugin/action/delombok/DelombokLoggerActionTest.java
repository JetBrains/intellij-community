package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokLoggerActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokLoggerAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/log";
  }

  public void testLog() throws Exception {
    // java.util.logging.Logger is not in mockJdk21
    myFixture.addClass("""
                         package java.util.logging;
                         public class Logger {
                           public static Logger getLogger(String name) {
                                 return null;
                             }
                         }""");
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
