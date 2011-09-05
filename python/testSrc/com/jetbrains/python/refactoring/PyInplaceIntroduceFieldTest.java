package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.testFramework.LightPlatformTestCase;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.field.PyIntroduceFieldHandler;

/**
 * @author yole
 */
public class PyInplaceIntroduceFieldTest extends PyLightFixtureTestCase {
  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public PyInplaceIntroduceFieldTest() {
    PyLightFixtureTestCase.initPlatformPrefix();
  }

  public void testPy4453() {
    doTest();
  }

  private void doTest() {
    String name = getTestName(true);
    myFixture.configureByFile("refactoring/inplaceIntroduceField/" + name + ".py");
    final boolean enabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(LightPlatformTestCase.getProject());
    try {
      templateManager.setTemplateTesting(true);
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      PyIntroduceFieldHandler handler = new PyIntroduceFieldHandler();
      final IntroduceOperation introduceOperation = new IntroduceOperation(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "a",
                                                                           false, false);
      introduceOperation.setReplaceAll(true);
      handler.performAction(introduceOperation);

      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      assert state != null;
      state.gotoEnd(false);
      myFixture.checkResultByFile("refactoring/inplaceIntroduceField/" +  name + ".after.py");
    }
    finally {
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
      templateManager.setTemplateTesting(false);
    }
  }
}
