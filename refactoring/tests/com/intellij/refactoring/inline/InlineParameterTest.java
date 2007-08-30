package com.intellij.refactoring.inline;

import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class InlineParameterTest extends LightCodeInsightTestCase {
  private LanguageLevel myPreviousLanguageLevel;

  protected void setUp() throws Exception {
    super.setUp();
    myPreviousLanguageLevel = getPsiManager().getEffectiveLanguageLevel();
    getPsiManager().setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    getPsiManager().setEffectiveLanguageLevel(myPreviousLanguageLevel);
    super.tearDown();
  }

  protected ProjectJdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  public void testSameValue() throws Exception {
    doTest();
  }

  public void testNullValue() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineParameter/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(null, fileName + ".after", true);
  }

  private void performAction() {
    final PsiElement element = TargetElementUtil.findTargetElement(myEditor,
                                                                   TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    InlineParameterHandler.invoke(getProject(), myEditor, (PsiParameter) element);
  }
}