package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;

public class PushDownProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPushDown.PushDownProcessor");
  private MemberInfo myMemberInfos[];
  private PsiClass myClass;
  private boolean myIsPreviewUsages;
  private JavaDocPolicy myJavaDocPolicy;

  public PushDownProcessor(Project project, MemberInfo[] memberInfos, PsiClass aClass, JavaDocPolicy javaDocPolicy, boolean previewUsages,
                           Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myMemberInfos = memberInfos;
    myClass = aClass;
    myIsPreviewUsages = previewUsages;
    myJavaDocPolicy = javaDocPolicy;
  }

  protected String getCommandName() {
    return PushDownHandler.REFACTORING_NAME;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new PushDownUsageViewDescriptor(myClass, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    PsiManager manager = PsiManager.getInstance(myProject);
    final PsiSearchHelper searchHelper = manager.getSearchHelper();
    final PsiClass[] inheritors = searchHelper.findInheritors(myClass, GlobalSearchScope.projectScope(myProject), false);
    UsageInfo[] usages = new UsageInfo[inheritors.length];
    for (int i = 0; i < inheritors.length; i++) {
      usages[i] = new UsageInfo(inheritors[i]);
    }
    return usages;
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(myClass, myMemberInfos);
    pushDownConflicts.checkSourceClassConflicts();

    if (usages[0].length == 0) {
      final String message = (myClass.isInterface() ? "Interface " : "Class ")
              + myClass.getQualifiedName() + " does not have inheritors.\n" +
              "Pushing memebers down will result in them being deleted. Continue?";
      final int answer = Messages.showYesNoDialog(message, "Push Down", Messages.getWarningIcon());
      if (answer != 0) return false;
    }
    for (int i = 0; i < usages[0].length; i++) {
      final PsiElement element = usages[0][i].getElement();
      if(element instanceof PsiClass) {
        pushDownConflicts.checkTargetClassConflicts((PsiClass) element);
      }
    }
    if(pushDownConflicts.isAnyConflicts()) {
      final String[] conflicts = pushDownConflicts.getConflicts();
      ConflictsDialog dialog = new ConflictsDialog(conflicts, myProject);
      dialog.show();
      if(!dialog.isOK()) return false;
    }

    prepareSuccessful();

    return true;
  }

  protected void refreshElements(PsiElement[] elements) {
    if(elements.length == 1 && elements[0] instanceof PsiClass) {
      myClass = (PsiClass) elements[0];
    }
    else {
      LOG.assertTrue(false);
    }
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return super.isPreviewUsages(usages) || myIsPreviewUsages;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];

        if(usage.getElement() instanceof PsiClass) {
          pushDownToClass((PsiClass) usage.getElement());
        }
      }
      removeFromTargetClass();
    }
    catch (IncorrectOperationException e) {
      LOG.assertTrue(false);
    }
  }

  private void removeFromTargetClass() throws IncorrectOperationException {
    for (int i = 0; i < myMemberInfos.length; i++) {
      MemberInfo memberInfo = myMemberInfos[i];
      final PsiElement member = memberInfo.getMember();

      if(member instanceof PsiField) {
        member.delete();
      }
      else if(member instanceof PsiMethod) {
        if(memberInfo.isToAbstract()) {
          final PsiMethod method = (PsiMethod) member;
          if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            method.getModifierList().setModifierProperty(PsiModifier.PROTECTED, true);
          }
          RefactoringUtil.abstractizeMethod(myClass, method);
          myJavaDocPolicy.processOldJavaDoc(method.getDocComment());
        }
        else {
          member.delete();
        }
      }
      else if(member instanceof PsiClass) {
        if(Boolean.FALSE.equals(memberInfo.getOverrides())) {
          RefactoringUtil.removeFromReferenceList(myClass.getImplementsList(), (PsiClass) member);
        }
        else {
          member.delete();
        }
      }
    }
  }


  private void pushDownToClass(PsiClass targetClass) throws IncorrectOperationException {
    PsiElementFactory factory = myClass.getManager().getElementFactory();
    for (int i = 0; i < myMemberInfos.length; i++) {
      MemberInfo memberInfo = myMemberInfos[i];
      final PsiElement member = memberInfo.getMember();

      if(member instanceof PsiField) {
        targetClass.add(member);
      }
      else if(member instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) member;

        if(targetClass.findMethodBySignature(method, false) == null) {
          PsiMethod newMethod = (PsiMethod) targetClass.add(method);
          if(memberInfo.isToAbstract()) {
            if (newMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
              newMethod.getModifierList().setModifierProperty(PsiModifier.PROTECTED, true);
            }
            myJavaDocPolicy.processNewJavaDoc(newMethod.getDocComment());
          }
        }
      }
      else if(member instanceof PsiClass) {
        if(Boolean.FALSE.equals(memberInfo.getOverrides())) {
          final PsiClass aClass = (PsiClass) member;
          if (!targetClass.isInheritor(aClass, false)) {
            PsiJavaCodeReferenceElement classRef = factory.createClassReferenceElement(aClass);
            targetClass.getImplementsList().add(classRef);
          }
        }
        else {
          targetClass.add(member);
        }
      }
    }
  }

}
