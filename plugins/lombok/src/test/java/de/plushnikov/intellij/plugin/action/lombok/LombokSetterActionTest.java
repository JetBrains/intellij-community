package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class LombokSetterActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new LombokSetterAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/lombok/setter";
  }

  public void testSetterSimple() throws Exception {
    doTest();
  }

  public void testSetterStatic() throws Exception {
    doTest();
  }

  public void testSetterDifferentVisibility() throws Exception {
    doTest();
  }

  public void testSetterWithAnnotationPresentOnClass() throws Exception {
    doTest();
  }

  public void testSetterWithAnnotationChange() throws Exception {
    doTest();
  }

  public void testSetterWithCustomCode() throws Exception {
    doTest();
  }
}
