package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokToStringActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokToStringAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/tostring";
  }

  public void testClassToStringSimple() throws Exception {
    doTest();
  }

  public void testClassToStringOf() throws Exception {
    doTest();
  }

  public void testClassToStringNoGetters() throws Exception {
    doTest();
  }

  public void testClassToStringExclude() throws Exception {
    doTest();
  }

  public void testClassToStringCallSuper() throws Exception {
    doTest();
  }
}
