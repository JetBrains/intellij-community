package com.intellij.refactoring.inline;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;

/**
 * @author ven
 */
public class InlineLocalTest extends CodeInsightTestCase {
  public void testInference () throws Exception {
    doTest();
  }

  public void testQualifier () throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    String fileName = "/refactoring/inlineLocal/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(fileName + ".after");
  }

  private void performAction() {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof PsiLocalVariable);

    new InlineLocalHandler().invoke(myProject, myEditor, (PsiLocalVariable)element);
  }
}
