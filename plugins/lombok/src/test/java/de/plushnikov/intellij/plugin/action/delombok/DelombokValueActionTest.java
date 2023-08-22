package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokValueActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokValueAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/value";
  }

  public void testValuePlain() throws Exception {
    doTest();
  }

  public void testValueNonFinalOnField() throws Exception {
    doTest();
  }

  public void testValueNonFinalOnClass() throws Exception {
    doTest();
  }
}
