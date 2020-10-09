package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokBuilderActionTest extends LombokLightActionTestCase {

  protected AnAction getAction() {
    return new DelombokBuilderAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/builder";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testBuilderSimple() throws Exception {
    doTest();
  }

  public void testBuilderSimplePredefined() throws Exception {
    doTest();
  }

  public void testBuilderAtMethodSimple() throws Exception {
    doTest();
  }

  public void testBuilderAtMethodSimplePredefined() throws Exception {
    doTest();
  }

  public void testBuilderAtConstructorSimple() throws Exception {
    doTest();
  }

  public void testBuilderAtConstructorSimplePredefined() throws Exception {
    doTest();
  }

  public void testBuilderSingularMap() throws Exception {
    doTest();
  }

  public void testBuilderSimpleWithSetterPrefix() throws Exception {
    doTest();
  }
}
