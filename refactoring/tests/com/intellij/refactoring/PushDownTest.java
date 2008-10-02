/*
 * User: anna
 * Date: 13-Mar-2008
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.util.List;

public class PushDownTest extends LightCodeInsightTestCase {
  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean failure) throws Exception {
    final String filePath = "/refactoring/pushDown/" + getTestName(false)+ ".java";
    configureByFile(filePath);

    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on member name", targetElement instanceof PsiMember);

    final PsiMember psiMember = (PsiMember)targetElement;

    final PsiClass[] classes = ((PsiJavaFile)psiMember.getContainingFile()).getClasses();

    final MemberInfo memberInfo = new MemberInfo(psiMember);
    memberInfo.setChecked(true);
    new PushDownProcessor(getProject(), new MemberInfo[]{memberInfo}, classes[0], new JavaDocPolicy(JavaDocPolicy.ASIS)){
      @Override
      protected boolean showConflicts(final List<String> conflicts) {
        if (failure ? conflicts.isEmpty() : !conflicts.isEmpty()) {
          fail(failure ? "Conflict was not detected" : "False conflict was detected");
        }
        return super.showConflicts(conflicts);
      }
    }.run();

    checkResultByFile(filePath + ".after");
  }

  public void testTypeParameter() throws Exception {
    doTest();
  }

  public void testFieldTypeParameter() throws Exception {
    doTest();
  }

  public void testBodyTypeParameter() throws Exception {
    doTest();
  }

  public void testDisagreeTypeParameter() throws Exception {
    doTest(true);
  }
}