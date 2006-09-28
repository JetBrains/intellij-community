package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;

import java.util.List;

/**
 * @author dsl
 */
public class PushDownHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("push.members.down.title");

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);

    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.push.members.from"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MEMBERS_PUSH_DOWN, project);
        return;
      }

      if (element instanceof PsiClass) {
        if (element instanceof JspClass) {
          RefactoringMessageUtil.showNotSupportedForJspClassesError(project, REFACTORING_NAME, HelpID.MEMBERS_PUSH_DOWN);
          return;
        }
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    PsiElement element = elements[0];
    PsiClass aClass;
    PsiElement aMember = null;

    if (element instanceof PsiClass) {
      aClass = (PsiClass) element;
    } else if (element instanceof PsiMethod) {
      aClass = ((PsiMethod) element).getContainingClass();
      aMember = element;
    } else if (element instanceof PsiField) {
      aClass = ((PsiField) element).getContainingClass();
      aMember = element;
    } else
      return;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(aClass, new MemberInfo.Filter() {
      public boolean includeMember(PsiMember element) {
        return true;
      }
    });
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(aClass);
    PsiManager manager = aClass.getManager();

    for (MemberInfo member : members) {
      if (manager.areElementsEquivalent(member.getMember(), aMember)) {
        member.setChecked(true);
        break;
      }
    }
    PushDownDialog dialog = new PushDownDialog(
            project,
            members.toArray(new MemberInfo[members.size()]),
            aClass);
    dialog.show();
  }
}