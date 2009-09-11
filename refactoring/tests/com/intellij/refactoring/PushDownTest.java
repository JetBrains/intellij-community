/*
 * User: anna
 * Date: 13-Mar-2008
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.*;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.util.ArrayList;
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

    assert classes.length > 0;

    final List<MemberInfo> membersToMove = new ArrayList<MemberInfo>();

    final PsiField fieldByName = classes[0].findFieldByName("fieldToMove", false);
    if (fieldByName != null) {
      final MemberInfo memberInfo = new MemberInfo(fieldByName);
      memberInfo.setChecked(true);
      membersToMove.add(memberInfo);
    }

    final MemberInfo memberInfo = new MemberInfo(psiMember);
    memberInfo.setChecked(true);
    membersToMove.add(memberInfo);

    new PushDownProcessor(getProject(), membersToMove.toArray(new MemberInfo[membersToMove.size()]), classes[0], new DocCommentPolicy(
      DocCommentPolicy.ASIS)){
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

  public void testFieldAndReferencedClass() throws Exception {
    doTest();
  }

  public void testFieldAndStaticReferencedClass() throws Exception {
    doTest();
  }
}
