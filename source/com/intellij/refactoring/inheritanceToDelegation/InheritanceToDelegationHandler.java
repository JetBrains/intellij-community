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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class InheritanceToDelegationHandler implements RefactoringActionHandler, InheritanceToDelegationDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationHandler");
  public static final String REFACTORING_NAME = "Replace Inheritance With Delegation";

  private Project myProject;
  private PsiClass myClass;
  private static final MemberInfo.Filter MEMBER_INFO_FILTER = new MemberInfo.Filter() {
    public boolean includeMember(PsiMember element) {
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        return !method.isConstructor()
               && !method.hasModifierProperty(PsiModifier.STATIC)
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
        String message =
                "Cannot perform the refactoring.\n" +
                "The caret should be positioned inside the class to be refactored.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.INHERITANCE_TO_DELEGATION*/, project);
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

    myProject = project;
    myClass = (PsiClass) elements[0];

    if (myClass.isInterface()) {
      String message =
              "Cannot perform the refactoring.\n" +
              myClass.getQualifiedName() + " is an interface.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.INHERITANCE_TO_DELEGATION*/, project);
      return;
    }

    if (!myClass.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, myClass);
      return;
    }

    final PsiClass[] bases = myClass.getSupers();
    if (bases.length == 0 || bases.length == 1 && "java.lang.Object".equals(bases[0].getQualifiedName())) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Class " + myClass.getQualifiedName() + " does not have base classes or interfaces.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.INHERITANCE_TO_DELEGATION*/, project);
      return;
    }

    final HashMap<PsiClass,MemberInfo[]> basesToMemberInfos = new HashMap<PsiClass, MemberInfo[]>();

    for (int i = 0; i < bases.length; i++) {
      PsiClass base = bases[i];
      basesToMemberInfos.put(base, createBaseClassMemberInfos(base));
    }


    new InheritanceToDelegationDialog(project, myClass,
            bases, basesToMemberInfos, this
    ).show();
  }

  private MemberInfo[] createBaseClassMemberInfos(PsiClass baseClass) {
    final PsiClass deepestBase = RefactoringHierarchyUtil.getDeepestNonObjectBase(baseClass);
    LOG.assertTrue(deepestBase != null);

    final MemberInfoStorage memberInfoStorage = new MemberInfoStorage(baseClass, MEMBER_INFO_FILTER);

    ArrayList<MemberInfo> memberInfoList = new ArrayList<MemberInfo>(memberInfoStorage.getClassMemberInfos(deepestBase));
    MemberInfo[] memberInfos = memberInfoStorage.getMemberInfosList(deepestBase);
    for (int i = 0; i < memberInfos.length; i++) {
      final MemberInfo memberInfo = memberInfos[i];

      memberInfoList.add(memberInfo);
    }

    final MemberInfo[] targetClassMemberInfos = memberInfoList.toArray(new MemberInfo[memberInfoList.size()]);
    return targetClassMemberInfos;
  }

  public void run(final InheritanceToDelegationDialog dialog) {
    final MemberInfo[] selectedMemberInfos = dialog.getSelectedMemberInfos();
    final ArrayList<PsiClass> implementedInterfaces = new ArrayList<PsiClass>();
    final ArrayList<PsiMethod> delegatedMethods = new ArrayList<PsiMethod>();

    for (int i = 0; i < selectedMemberInfos.length; i++) {
      MemberInfo memberInfo = selectedMemberInfos[i];
      final PsiElement member = memberInfo.getMember();
      if (member instanceof PsiClass && Boolean.FALSE.equals(memberInfo.getOverrides())) {
        implementedInterfaces.add((PsiClass)member);
      } else if (member instanceof PsiMethod) {
        delegatedMethods.add((PsiMethod)member);
      }
    }
    new InheritanceToDelegationProcessor(myProject, myClass,
            dialog.getSelectedTargetClass(), dialog.getFieldName(), dialog.getInnerClassName(),
            implementedInterfaces.toArray(new PsiClass[implementedInterfaces.size()]),
            delegatedMethods.toArray(new PsiMethod[delegatedMethods.size()]),
            dialog.isGenerateGetter(), dialog.isGenerateGetter(),
            dialog.isPreviewUsages(), new Runnable() {
              public void run() {
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
              }
            }
    ).run(null);
  }
}
