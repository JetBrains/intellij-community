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
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.inlineSuperClass.usageInfo.*;
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
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InlineSuperClassRefactoringProcessor extends FixableUsagesRefactoringProcessor {
  public static final Logger LOG = Logger.getInstance("#" + InlineSuperClassRefactoringProcessor.class.getName());

  private final PsiClass mySuperClass;
  private final PsiClass myTargetClass;
  private MemberInfo[] myMemberInfos;

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
  }

  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new InlineSuperClassUsageViewDescriptor(mySuperClass);
  }


  protected void findUsages(@NotNull final List<FixableUsageInfo> usages) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final PsiSubstitutor superClassSubstitutor =
      TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, myTargetClass, PsiSubstitutor.EMPTY);
    final PsiClassType targetClassType = elementFactory.createType(myTargetClass, superClassSubstitutor);
    ReferencesSearch.search(mySuperClass).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiJavaCodeReferenceElement) {
          final PsiImportStaticStatement staticImportStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
          if (staticImportStatement != null) {
            usages.add(new ReplaceStaticImportUsageInfo(staticImportStatement, myTargetClass));
          } else {
            final PsiImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
            if (importStatement != null) {
              usages.add(new RemoveImportUsageInfo(importStatement));
            }
            else {
              final PsiElement parent = element.getParent();
              if (parent instanceof PsiTypeElement) {
                final PsiType superClassType = ((PsiTypeElement)parent).getType();
                PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper);
                usages.add(new ReplaceWithSubtypeUsageInfo(((PsiTypeElement)parent), elementFactory.createType(myTargetClass, subst)));
              }
              else if (parent instanceof PsiReferenceList) {
                final PsiElement pparent = parent.getParent();
                if (pparent instanceof PsiClass) {
                  final PsiClass inheritor = (PsiClass)pparent;
                  if (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList())) {
                    usages.add(new ReplaceExtendsListUsageInfo((PsiJavaCodeReferenceElement)element, mySuperClass, myTargetClass));
                  }
                }
              } else if (parent instanceof PsiNewExpression) {
                final PsiClassType newType = elementFactory
                  .createType(myTargetClass, getSuperClassSubstitutor(((PsiNewExpression)parent).getType(), targetClassType, resolveHelper));
                usages.add(new ReplaceConstructorUsageInfo(((PsiNewExpression)parent), myTargetClass, newType));
              } else if (parent instanceof PsiJavaCodeReferenceElement) {
                usages.add(new ReplaceReferenceUsageInfo(((PsiJavaCodeReferenceElement)parent).getQualifier(), myTargetClass));
              }
            }
          }
        }
        return true;
      }
    });
    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiMember member = memberInfo.getMember();
      for (PsiReference reference : ReferencesSearch.search(member, member.getUseScope(), true)) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiReferenceExpression &&
            ((PsiReferenceExpression)element).getQualifierExpression() instanceof PsiSuperExpression && PsiTreeUtil.isAncestor(myTargetClass, element, false)) {
          usages.add(new RemoveQualifierUsageInfo((PsiReferenceExpression)element));
        }
      }
    }
  }

  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    final List<String> conflicts = new ArrayList<String>();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(mySuperClass, myMemberInfos);
    for (MemberInfo info : myMemberInfos) {
      final PsiMember member = info.getMember();
      pushDownConflicts.checkMemberPlacementInTargetClassConflict(myTargetClass, member);
    }
    for (UsageInfo info : refUsages.get()) {
      final String conflict = ((FixableUsageInfo)info).getConflictMessage();
      if (conflict != null) {
        conflicts.add(conflict);
      }
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
    replaceInnerTypeUsages();
    super.performRefactoring(usages);
    try {
      mySuperClass.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void replaceInnerTypeUsages() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final PsiSubstitutor superClassSubstitutor =
      TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, myTargetClass, PsiSubstitutor.EMPTY);
    final PsiClassType targetClassType = elementFactory.createType(myTargetClass, superClassSubstitutor);
    final Map<UsageInfo, PsiElement> replacementMap = new HashMap<UsageInfo, PsiElement>();
    myTargetClass.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitTypeElement(final PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        final PsiType superClassType = typeElement.getType();
        if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
          PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper);
          replacementMap.put(new UsageInfo(typeElement), elementFactory.createTypeElement(elementFactory.createType(myTargetClass, subst)));
        }
      }

      @Override
      public void visitNewExpression(final PsiNewExpression expression) {
        super.visitNewExpression(expression);
        final PsiType superClassType = expression.getType();
        if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
          PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper);
          try {
            replacementMap.put(new UsageInfo(expression), elementFactory.createExpressionFromText("new " + elementFactory.createType(myTargetClass, subst).getCanonicalText() + expression.getArgumentList().getText(), expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    });
    try {
      for (Map.Entry<UsageInfo,PsiElement> elementEntry : replacementMap.entrySet()) {
        final PsiElement element = elementEntry.getKey().getElement();
        if (element != null) {
          element.replace(elementEntry.getValue());
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiSubstitutor getSuperClassSubstitutor(final PsiType superClassType, final PsiClassType targetClassType, final PsiResolveHelper resolveHelper) {
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(myTargetClass)) {
      subst = subst.put(typeParameter,
                        resolveHelper.getSubstitutionForTypeParameter(typeParameter, targetClassType, superClassType, false,
                                                                      PsiUtil.getLanguageLevel(myTargetClass)));
    }
    return subst;
  }

  protected String getCommandName() {
    return InlineSuperClassRefactoringHandler.REFACTORING_NAME;
  }
}