package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;
import org.jetbrains.annotations.NotNull;

public class DelombokLoggerActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokLoggerAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/log";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_DESCRIPTOR;
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
