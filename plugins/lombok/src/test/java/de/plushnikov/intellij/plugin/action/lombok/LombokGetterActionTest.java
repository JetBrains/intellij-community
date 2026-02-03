package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.openapi.actionSystem.AnAction;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class LombokGetterActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new LombokGetterAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/lombok/getter";
  }

  public void testGetterSimple() throws Exception {
    doTest();
  }

  public void testGetterStatic() throws Exception {
    doTest();
  }

  public void testGetterDifferentVisibility() throws Exception {
    doTest();
  }

  public void testGetterWithAnnotationPresentOnClass() throws Exception {
    doTest();
  }

  public void testGetterWithAnnotationChange() throws Exception {
    doTest();
  }

  public void testGetterWithCustomCode() throws Exception {
    doTest();
  }
}
