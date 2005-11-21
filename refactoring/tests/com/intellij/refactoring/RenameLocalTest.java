package com.intellij.refactoring;

import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.TargetElementUtil;

/**
 * @author ven
 */
public class RenameLocalTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameLocal/";

  public void testIDEADEV3320() throws Exception {
    doTest("f");
  }

  private void doTest(final String newName) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
                                                             TargetElementUtil.ELEMENT_NAME_ACCEPTED |
                                                             TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
