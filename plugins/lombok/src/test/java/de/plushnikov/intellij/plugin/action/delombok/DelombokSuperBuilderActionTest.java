package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokSuperBuilderActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokSuperBuilderAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/superbuilder";
  }

  public void testSuperBuilderJacksonized() throws Exception {
    doTest();
  }

  public void testSuperBuilderWithBuilderDefault() throws Exception {
    doTest();
  }
}
