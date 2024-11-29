package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class LombokDataActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new LombokDataAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/lombok/data";
  }

  public void testDataSimple() throws Exception {
    doTest();
  }

  public void testDataWithCustomCodeSetterGetter() throws Exception {
    doTest();
  }
}
