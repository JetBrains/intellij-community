package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author ven
 */
public class InlineLocalTest extends LightCodeInsightTestCase {
  private LanguageLevel myPreviousLanguageLevel;

  protected void setUp() throws Exception {
    super.setUp();
    myPreviousLanguageLevel = getPsiManager().getEffectiveLanguageLevel();
    getPsiManager().setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected ProjectJdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  protected void tearDown() throws Exception {
    getPsiManager().setEffectiveLanguageLevel(myPreviousLanguageLevel);
    super.tearDown();
  }

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

  public void testIDEADEV12244 () throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    String fileName = "/refactoring/inlineLocal/" + name + ".java";
    configureByFile(fileName);
    performInline(getProject(), myEditor);
    checkResultByFile(fileName + ".after");
  }

  public static void performInline(Project project, Editor editor) {
    PsiElement element = TargetElementUtil.findTargetElement(editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof PsiLocalVariable);

    new InlineLocalHandler().invoke(project, editor, (PsiLocalVariable)element);
  }
}
