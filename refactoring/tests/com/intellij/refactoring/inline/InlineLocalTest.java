package com.intellij.refactoring.inline;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

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

  public void testIDEADEV950 () throws Exception {
    doTest();
  }

  public void testNoRedundantCasts () throws Exception {
    doTest();
  }

  public void testIDEADEV9404 () throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    String fileName = "/refactoring/inlineLocal/" + name + ".java";
    configureByFile(fileName);
    performInline(myProject, myEditor);
    checkResultByFile(fileName + ".after");
  }

  public static void performInline(Project project, Editor editor) {
    PsiElement element = TargetElementUtil.findTargetElement(editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof PsiLocalVariable);

    new InlineLocalHandler().invoke(project, editor, (PsiLocalVariable)element);
  }
}
