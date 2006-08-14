package com.intellij.refactoring.memberPushDown;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PushDownProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPushDown.PushDownProcessor");
  private MemberInfo myMemberInfos[];
  private PsiClass myClass;
  private JavaDocPolicy myJavaDocPolicy;

  public PushDownProcessor(Project project,
                           MemberInfo[] memberInfos,
                           PsiClass aClass,
                           JavaDocPolicy javaDocPolicy) {
    super(project);
    myMemberInfos = memberInfos;
    myClass = aClass;
    myJavaDocPolicy = javaDocPolicy;
  }

  protected String getCommandName() {
    return PushDownHandler.REFACTORING_NAME;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new PushDownUsageViewDescriptor(myClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    PsiManager manager = PsiManager.getInstance(myProject);
    final PsiSearchHelper searchHelper = manager.getSearchHelper();
    final PsiClass[] inheritors = searchHelper.findInheritors(myClass, myClass.getUseScope(), false);
    UsageInfo[] usages = new UsageInfo[inheritors.length];
    for (int i = 0; i < inheritors.length; i++) {
      usages[i] = new UsageInfo(inheritors[i]);
    }
    return usages;
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(myClass, myMemberInfos);
    pushDownConflicts.checkSourceClassConflicts();

    if (usagesIn.length == 0) {
      String noInheritors = myClass.isInterface() ?
                            RefactoringBundle.message("interface.0.does.not.have.inheritors", myClass.getQualifiedName()) :
                            RefactoringBundle.message("class.0.does.not.have.inheritors", myClass.getQualifiedName());
      final String message = noInheritors + "\n" + RefactoringBundle.message("push.down.will.delete.members");
      final int answer = Messages.showYesNoDialog(message, PushDownHandler.REFACTORING_NAME, Messages.getWarningIcon());
      if (answer != 0) return false;
    }
    for (UsageInfo usage : usagesIn) {
      final PsiElement element = usage.getElement();
      if (element instanceof PsiClass) {
        pushDownConflicts.checkTargetClassConflicts((PsiClass)element);
      }
    }

    return showConflicts(pushDownConflicts.getConflicts());
  }

  protected void refreshElements(PsiElement[] elements) {
    if(elements.length == 1 && elements[0] instanceof PsiClass) {
      myClass = (PsiClass) elements[0];
    }
    else {
      LOG.assertTrue(false);
    }
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      encodeRefs();
      for (UsageInfo usage : usages) {
        if (usage.getElement() instanceof PsiClass) {
          final PsiClass targetClass = (PsiClass)usage.getElement();
          pushDownToClass(targetClass);
        }
      }
      removeFromTargetClass();
    }
    catch (IncorrectOperationException e) {
      LOG.assertTrue(false);
    }
  }

  private static final Key<Boolean> REMOVE_QUALIFIER_KEY = Key.create("REMOVE_QUALIFIER_KEY");
  private static final Key<Boolean> REPLACE_QUALIFIER_KEY = Key.create("REPLACE_QUALIFIER_KEY");

  private void encodeRefs() {
    final Set<PsiMember> movedMembers = new HashSet<PsiMember>();
    for (MemberInfo memberInfo : myMemberInfos) {
      movedMembers.add(memberInfo.getMember());
    }

    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiMember member = memberInfo.getMember();
      member.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          encodeRef(expression, movedMembers, expression);
          super.visitReferenceExpression(expression);
        }

        public void visitNewExpression(PsiNewExpression expression) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            encodeRef(classReference, movedMembers, expression);
          }
          super.visitNewExpression(expression);
        }
      });
      ChangeContextUtil.encodeContextInfo(member, false);
    }
  }

  private void encodeRef(final PsiJavaCodeReferenceElement expression, final Set<PsiMember> movedMembers, final PsiElement toPut) {
    final PsiElement resolved = expression.resolve();
    if (movedMembers.contains(resolved)) {
      if (expression.getQualifier() == null) {
        toPut.putCopyableUserData(REMOVE_QUALIFIER_KEY, Boolean.TRUE);
      } else {
        final PsiElement qualifier = expression.getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement &&
            ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(myClass)) {
          toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, Boolean.TRUE);
        }
      }
    }
  }

  private void decodeRefs(final PsiMember member, final PsiClass targetClass) {
    try {
      ChangeContextUtil.decodeContextInfo(member, null, null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
    member.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        decodeRef(expression, factory, targetClass, expression);
        super.visitReferenceExpression(expression);
      }

      public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference != null) decodeRef(classReference, factory, targetClass, expression);
        super.visitNewExpression(expression);
      }
    });
  }

  private void decodeRef(final PsiJavaCodeReferenceElement ref,
                         final PsiElementFactory factory,
                         final PsiClass targetClass,
                         final PsiElement toGet) {
    try {
      if (toGet.getCopyableUserData(REMOVE_QUALIFIER_KEY) != null) {
        toGet.putCopyableUserData(REMOVE_QUALIFIER_KEY, null);
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier != null) qualifier.delete();
      }
      else if (toGet.getCopyableUserData(REPLACE_QUALIFIER_KEY) != null) {
        toGet.putCopyableUserData(REPLACE_QUALIFIER_KEY, null);
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier != null) {
          if (ref instanceof PsiReferenceExpression) {
            qualifier.replace(factory.createReferenceExpression(targetClass));
          }
          else {
            qualifier.replace(factory.createReferenceElementByType(factory.createType(targetClass)));
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void removeFromTargetClass() throws IncorrectOperationException {
    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiElement member = memberInfo.getMember();

      if (member instanceof PsiField) {
        member.delete();
      }
      else if (member instanceof PsiMethod) {
        if (memberInfo.isToAbstract()) {
          final PsiMethod method = (PsiMethod)member;
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
      else if (member instanceof PsiClass) {
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          RefactoringUtil.removeFromReferenceList(myClass.getImplementsList(), (PsiClass)member);
        }
        else {
          member.delete();
        }
      }
    }
  }


  private void pushDownToClass(PsiClass targetClass) throws IncorrectOperationException {
    PsiElementFactory factory = myClass.getManager().getElementFactory();
    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiMember member = memberInfo.getMember();
      PsiMember newMember = null;
      if (member instanceof PsiField) {
        newMember = (PsiMember)targetClass.add(member);
      }
      else if (member instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)member;

        if (targetClass.findMethodBySignature(method, false) == null) {
          newMember = (PsiMethod)targetClass.add(method);
          if (memberInfo.isToAbstract()) {
            if (newMember.hasModifierProperty(PsiModifier.PRIVATE)) {
              newMember.getModifierList().setModifierProperty(PsiModifier.PROTECTED, true);
            }
            myJavaDocPolicy.processNewJavaDoc(((PsiMethod)newMember).getDocComment());
          }
        }
      }
      else if (member instanceof PsiClass) {
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          final PsiClass aClass = (PsiClass)member;
          if (!targetClass.isInheritor(aClass, false)) {
            PsiJavaCodeReferenceElement classRef = factory.createClassReferenceElement(aClass);
            targetClass.getImplementsList().add(classRef);
          }
        }
        else {
          newMember = (PsiMember)targetClass.add(member);
        }
      }

      if (newMember != null) {
        decodeRefs(newMember, targetClass);
      }
    }

  }

}
