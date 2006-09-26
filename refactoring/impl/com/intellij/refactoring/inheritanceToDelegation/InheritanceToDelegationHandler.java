/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.08.2002
 * Time: 17:17:27
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public class InheritanceToDelegationHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationHandler");
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.inheritance.with.delegation.title");

  private static final MemberInfo.Filter MEMBER_INFO_FILTER = new MemberInfo.Filter() {
    public boolean includeMember(PsiMember element) {
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        return !method.hasModifierProperty(PsiModifier.STATIC)
               && !method.hasModifierProperty(PsiModifier.PRIVATE);
      }
      else if (element instanceof PsiClass && ((PsiClass)element).isInterface()) {
        return true;
      }
      return false;
    }
  };


  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INHERITANCE_TO_DELEGATION, project);
        return;
      }

      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    final PsiClass aClass = (PsiClass)elements[0];

    if (aClass.isInterface()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("class.is.interface", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INHERITANCE_TO_DELEGATION, project);
      return;
    }

    if (aClass instanceof JspClass) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INHERITANCE_TO_DELEGATION, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    final PsiClass[] bases = aClass.getSupers();
    @NonNls final String javaLangObject = "java.lang.Object";
    if (bases.length == 0 || bases.length == 1 && javaLangObject.equals(bases[0].getQualifiedName())) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("class.does.not.have.base.classes.or.interfaces", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INHERITANCE_TO_DELEGATION, project);
      return;
    }

    final HashMap<PsiClass,MemberInfo[]> basesToMemberInfos = new HashMap<PsiClass, MemberInfo[]>();

    for (PsiClass base : bases) {
      basesToMemberInfos.put(base, createBaseClassMemberInfos(base));
    }


    new InheritanceToDelegationDialog(project, aClass,
                                      bases, basesToMemberInfos).show();
  }

  private static MemberInfo[] createBaseClassMemberInfos(PsiClass baseClass) {
    final PsiClass deepestBase = RefactoringHierarchyUtil.getDeepestNonObjectBase(baseClass);
    LOG.assertTrue(deepestBase != null);

    final MemberInfoStorage memberInfoStorage = new MemberInfoStorage(baseClass, MEMBER_INFO_FILTER);

    ArrayList<MemberInfo> memberInfoList = new ArrayList<MemberInfo>(memberInfoStorage.getClassMemberInfos(deepestBase));
    MemberInfo[] memberInfos = memberInfoStorage.getMemberInfosList(deepestBase);
    for (final MemberInfo memberInfo : memberInfos) {
      memberInfoList.add(memberInfo);
    }

    return memberInfoList.toArray(new MemberInfo[memberInfoList.size()]);
  }
}
