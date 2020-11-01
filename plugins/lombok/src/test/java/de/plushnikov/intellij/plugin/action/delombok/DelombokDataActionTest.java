package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokDataActionTest extends LombokLightActionTestCase {

  @Override
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

  public void testDataWithAnnotations() throws Exception {
    doTest();
  }
}
