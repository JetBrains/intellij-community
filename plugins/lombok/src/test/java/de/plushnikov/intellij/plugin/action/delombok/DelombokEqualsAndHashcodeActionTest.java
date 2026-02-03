package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokEqualsAndHashcodeActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new DelombokEqualsAndHashCodeAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/equalsandhashcode";
  }

  public void testEqualsAndHashCodeSimple() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeOf() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeNoGetters() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeExclude() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeCallSuper() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeIncludeExclude() throws Exception {
    doTest();
  }
}
