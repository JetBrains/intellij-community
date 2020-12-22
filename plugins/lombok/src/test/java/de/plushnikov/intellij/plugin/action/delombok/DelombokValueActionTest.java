package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class DelombokValueActionTest extends LombokLightActionTestCase {

  protected AnAction getAction() {
    return new DelombokValueAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/delombok/value";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testValuePlain() throws Exception {
    doTest();
  }

  public void testValueNonFinalOnField() throws Exception {
    doTest();
  }

  public void testValueNonFinalOnClass() throws Exception {
    doTest();
  }
}
