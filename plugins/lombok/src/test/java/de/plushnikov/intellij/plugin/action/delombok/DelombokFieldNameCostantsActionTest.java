package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokFieldNameCostantsActionTest extends LombokLightActionTestCase {

  protected AnAction getAction() {
    return new DelombokFieldNameConstantsAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/fieldnameconstants";
  }

  public void testFieldNameConstantsFields() throws Exception {
    doTest();
  }

  public void testFieldNameConstantsClass() throws Exception {
    doTest();
  }

}
