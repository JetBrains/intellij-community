package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class IntroduceConstantTest extends CodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/introduceConstant/";

  public void testInNonNls() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    convertLocal();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private void convertLocal() {
    PsiLocalVariable local = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new MockLocalToFieldHandler(getProject(), true).convertLocalToField(local, getEditor());
  }

  protected ProjectJdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}