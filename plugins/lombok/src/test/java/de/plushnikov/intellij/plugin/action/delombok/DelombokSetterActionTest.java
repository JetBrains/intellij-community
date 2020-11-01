package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokSetterActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokSetterAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/setter";
  }

  public void testSetterFields() throws Exception {
    doTest();
  }

  public void testSetterClass() throws Exception {
    doTest();
  }


}
