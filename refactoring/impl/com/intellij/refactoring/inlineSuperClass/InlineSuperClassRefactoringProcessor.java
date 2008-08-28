/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.inlineSuperClass.usageInfo.RemoveImportUsageInfo;
import com.intellij.refactoring.inlineSuperClass.usageInfo.ReplaceExtendsListUsageInfo;
import com.intellij.refactoring.inlineSuperClass.usageInfo.ReplaceWithSubtypeUsageInfo;
import com.intellij.refactoring.memberPushDown.PushDownConflicts;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class InlineSuperClassRefactoringProcessor extends FixableUsagesRefactoringProcessor {
  public static final Logger LOG = Logger.getInstance("#" + InlineSuperClassRefactoringProcessor.class.getName());

  private final PsiClass mySuperClass;
  private final PsiClass myTargetClass;
  private MemberInfo[] myMemberInfos;
  private PsiClassType myTargetClassType;

  public InlineSuperClassRefactoringProcessor(Project project, PsiClass superClass, final PsiClass targetClass) {
    super(project);
    mySuperClass = superClass;
    myTargetClass = targetClass;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySuperClass, new MemberInfo.Filter() {
      public boolean includeMember(PsiMember element) {
        return true;
      }
    });
    final List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySuperClass);
    for (MemberInfo member : members) {
      member.setChecked(true);
    }
    myMemberInfos = members.toArray(new MemberInfo[members.size()]);
    myTargetClassType = JavaPsiFacade.getInstance(myTargetClass.getProject()).getElementFactory()
      .createType(myTargetClass, TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, myTargetClass, PsiSubstitutor.EMPTY));
  }

  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new InlineSuperClassUsageViewDescriptor(mySuperClass);
  }


  protected void findUsages(@NotNull final List<FixableUsageInfo> usages) {
    ReferencesSearch.search(mySuperClass).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiJavaCodeReferenceElement) {
          final PsiImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
          if (importStatement != null) {
            usages.add(new RemoveImportUsageInfo(importStatement));
          }
          else {
            final PsiElement parent = element.getParent();
            if (parent instanceof PsiTypeElement) {
              usages.add(new ReplaceWithSubtypeUsageInfo((PsiTypeElement)parent, myTargetClass));//todo visibility
            }
            else if (parent instanceof PsiReferenceList) {
              final PsiElement pparent = parent.getParent();
              if (pparent instanceof PsiClass) {
                final PsiClass inheritor = (PsiClass)pparent;
                if (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList())) {
                  usages.add(new ReplaceExtendsListUsageInfo((PsiJavaCodeReferenceElement)element, mySuperClass, myTargetClass));
                }
              }
            }
          }
        }
        return true;
      }
    });
  }


  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    final List<String> conflicts = new ArrayList<String>();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(mySuperClass, myMemberInfos);
    for (MemberInfo info : myMemberInfos) {
      final PsiMember member = info.getMember();
      pushDownConflicts.checkMemberPlacementInTargetClassConflict(myTargetClass, member);
      ReferencesSearch.search(member, member.getResolveScope(), false).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
            final PsiExpression qualifier = referenceExpression.getQualifierExpression();
            if (qualifier != null) {
              final PsiType qualifierType = qualifier.getType();
              LOG.assertTrue(qualifierType != null);
              if (!TypeConversionUtil.isAssignable(qualifierType, myTargetClassType)) {
                conflicts.add("Non consistent substitution found for " + element.getText());
              }
            }
          }
          return true;
        }
      });
    }
    conflicts.addAll(pushDownConflicts.getConflicts());
    return showConflicts(conflicts);
  }

@Override
  protected boolean showConflicts(final List<String> conflicts) {
    if (!conflicts.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(StringUtil.join(conflicts, "\n"));
    }
    return super.showConflicts(conflicts);
  }


  protected void performRefactoring(final UsageInfo[] usages) {
    new PushDownProcessor(mySuperClass.getProject(), myMemberInfos, mySuperClass, new JavaDocPolicy(JavaDocPolicy.ASIS)).run();
    super.performRefactoring(usages);
    try {
      mySuperClass.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected String getCommandName() {
    return InlineSuperClassRefactoringHandler.REFACTORING_NAME;
  }
}