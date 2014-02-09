package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTest;

public class DelombokDataActionTest extends LombokLightActionTest {

  protected AnAction getAction() {
    return new DelombokDataAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/data";
  }

  public void testDataSimple() throws Exception {
    doTest();
  }

}
