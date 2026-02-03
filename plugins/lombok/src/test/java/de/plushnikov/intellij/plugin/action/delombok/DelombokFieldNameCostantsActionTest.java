package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokFieldNameCostantsActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokFieldNameConstantsAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/fieldnameconstants";
  }

  public void testFieldNameConstantsClass() throws Exception {
    doTest();
  }

  public void testFieldNameConstantsClassHandrolled() throws Exception {
    doTest();
  }

  public void testFieldNameConstantsEnumClass() throws Exception {
    doTest();
  }

  public void testFieldNameConstantsEnumHandrolled() throws Exception {
    doTest();
  }

}
