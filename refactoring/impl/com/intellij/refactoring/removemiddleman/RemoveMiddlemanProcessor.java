package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.removemiddleman.usageInfo.ChangeClassVisibilityUsageInfo;
import com.intellij.refactoring.removemiddleman.usageInfo.ChangeMethodVisibilityUsageInfo;
import com.intellij.refactoring.removemiddleman.usageInfo.DeleteMethod;
import com.intellij.refactoring.removemiddleman.usageInfo.InlineDelegatingCall;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveMiddlemanProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#" + RemoveMiddlemanProcessor.class.getName());

  private final PsiField field;
  private final PsiClass containingClass;
  private final MemberInfo[] myDelegateMethodInfos;
  private PsiMethod getter;

  public RemoveMiddlemanProcessor(PsiField field, MemberInfo[] memberInfos) {
    super(field.getProject());
    this.field = field;
    containingClass = field.getContainingClass();
    final Project project = field.getProject();
    final String propertyName = PropertyUtil.suggestPropertyName(project, field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    getter = PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, false);
    myDelegateMethodInfos = memberInfos;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos) {
    return new RemoveMiddlemanUsageViewDescriptor(field);
  }


  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    for (final MemberInfo memberInfo : myDelegateMethodInfos) {
      if (!memberInfo.isChecked()) return;
      final PsiMethod method = (PsiMethod)memberInfo.getMember();
      final Project project = method.getProject();
      final String getterName = PropertyUtil.suggestGetterName(project, field);
      final int[] paramPermutation = DelegationUtils.getParameterPermutation(method);
      final PsiMethod delegatedMethod = DelegationUtils.getDelegatedMethod(method);

      LOG.assertTrue(!DelegationUtils.isAbstract(method));
      @Modifier String visibility = PsiModifier.PRIVATE;
      visibility = processUsagesForMethod(memberInfo.isToAbstract(), method, paramPermutation, getterName, delegatedMethod, visibility, usages);
      final PsiMethod[] deepestSuperMethods = method.findDeepestSuperMethods();
      for (PsiMethod superMethod : deepestSuperMethods) {
        visibility = processUsagesForMethod(memberInfo.isToAbstract(), superMethod, paramPermutation, getterName, delegatedMethod, visibility, usages);
      }


      final PsiClass delegateClass = delegatedMethod.getContainingClass();
      if (VisibilityUtil.compare(visibility, VisibilityUtil.getVisibilityModifier(delegateClass.getModifierList())) < 0) {
        usages.add(new ChangeClassVisibilityUsageInfo(delegateClass, visibility));
      }

      if (!delegateClass.isInterface() && VisibilityUtil.compare(visibility, VisibilityUtil.getVisibilityModifier(delegatedMethod.getModifierList())) < 0) {
        usages.add(new ChangeMethodVisibilityUsageInfo(delegatedMethod, visibility));
      }
    }
  }


  @Modifier
  private String processUsagesForMethod(final boolean deleteMethodHierarchy, PsiMethod method, int[] paramPermutation, String getterName, PsiMethod delegatedMethod,
                                        @Modifier String visibility,
                                        List<FixableUsageInfo> usages) {
    for (PsiReference reference : ReferencesSearch.search(method)) {
      final PsiElement referenceElement = reference.getElement();
      final PsiMethodCallExpression call = (PsiMethodCallExpression)referenceElement.getParent();
      visibility = VisibilityUtil.getHighestVisibility(visibility, VisibilityUtil.getPossibleVisibility(delegatedMethod, referenceElement));
      final String access;
      if (PsiTreeUtil.getParentOfType(call, PsiClass.class) == containingClass) {
        access = field.getName();
      } else {
        access = getterName + "()";
        if (getter == null) {
          getter = PropertyUtil.generateGetterPrototype(field);
        }
      }
      usages.add(new InlineDelegatingCall(call, paramPermutation, access, delegatedMethod.getName()));
    }
    if (deleteMethodHierarchy) {
      usages.add(new DeleteMethod(method));
    }
    return visibility;
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    final Set<PsiClass> classesForGetters = new HashSet<PsiClass>();
    final Set<PsiMethod> methods = DelegationUtils.getDelegatingMethodsForField(field);
    for (final PsiMethod method : methods) {
      final PsiMethod[] deepestSuperMethods = method.findDeepestSuperMethods();
      for (PsiMethod superMethod : deepestSuperMethods) {
        classesForGetters.add(superMethod.getContainingClass());
      }
    }
    if (getter != null) {
      try {
        containingClass.add(getter);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      final PsiType returnType = getter.getReturnType();
      assert returnType != null;
      final String methodString = returnType.getCanonicalText() + ' ' + getter.getName() + "();";
      final PsiManager manager = containingClass.getManager();
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      for (PsiClass superClass : classesForGetters) {
        if (superClass.findMethodBySignature(getter, false) != null) {
          continue;
        }
        if (superClass.isInterface()) {
          try {
            superClass.add(elementFactory.createMethodFromText(methodString, null));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          try {
            superClass.add(elementFactory.createMethodFromText("public abstract " + methodString, null));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    super.performRefactoring(usageInfos);
  }

  protected String getCommandName() {
    return RefactorJBundle.message("exposed.delegation.command.name", containingClass.getName(), '.', field.getName());
  }
}
