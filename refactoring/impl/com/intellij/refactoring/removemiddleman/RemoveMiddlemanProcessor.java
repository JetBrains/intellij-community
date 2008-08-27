package com.intellij.refactoring.removemiddleman;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
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
  private final boolean deleteMethods;
  private PsiMethod getter;


  public RemoveMiddlemanProcessor(PsiField field, boolean deleteMethods) {
    super(field.getProject());
    this.field = field;
    containingClass = field.getContainingClass();
    final Project project = field.getProject();
    final String propertyName = PropertyUtil.suggestPropertyName(project, field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    getter = PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, false);
    this.deleteMethods = deleteMethods;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos) {
    return new RemoveMiddlemanUsageViewDescriptor(field);
  }


  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    final Set<PsiMethod> methods = DelegationUtils.getDelegatingMethodsForField(field);
    for (final PsiMethod method : methods) {
      final Project project = method.getProject();
      final String getterName = PropertyUtil.suggestGetterName(project, field);
      final int[] paramPermutation = DelegationUtils.getParameterPermutation(method);
      final PsiMethod delegatedMethod = DelegationUtils.getDelegatedMethod(method);
      final String delegatedMethodName = delegatedMethod.getName();

      final Set<PsiMethod> siblingMethods = MethodInheritanceUtils.calculateSiblingMethods(method);
      for (PsiMethod siblingMethod : siblingMethods) {
        processUsagesForMethod(siblingMethod, paramPermutation, getterName, delegatedMethodName, usages);
      }
      final PsiClass delegateClass = delegatedMethod.getContainingClass();
      if (!delegateClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        usages.add(new MakeClassPublic(delegateClass));
      }

      if (!delegateClass.isInterface() && !delegatedMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        usages.add(new MakeMethodPublic(delegatedMethod));
      }
      final PsiMethod[] superMethods = delegatedMethod.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        final PsiClass containingSuperClass = superMethod.getContainingClass();
        if (!containingSuperClass.isInterface() && !superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          usages.add(new MakeMethodPublic(superMethod));
        }
      }
    }
  }


  private void processUsagesForMethod(PsiMethod method,
                                      int[] paramPermutation,
                                      String getterName,
                                      String delegatedMethodName,
                                      List<FixableUsageInfo> usages) {

    for (PsiReference reference : ReferencesSearch.search(method)) {
      final PsiElement referenceElement = reference.getElement();
      final PsiMethodCallExpression call = (PsiMethodCallExpression)referenceElement.getParent();

      usages.add(new InlineDelegatingCall(call, paramPermutation, getterName, delegatedMethodName, field));
    }
    if (deleteMethods) {
      usages.add(new DeleteMethod(method));
    }
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    final Set<PsiClass> classesForGetters = new HashSet<PsiClass>();
    final Set<PsiMethod> methods = DelegationUtils.getDelegatingMethodsForField(field);
    for (final PsiMethod method : methods) {
      final Set<PsiMethod> siblingMethods = MethodInheritanceUtils.calculateSiblingMethods(method);
      for (PsiMethod siblingMethod : siblingMethods) {
        final PsiClass siblingClass = siblingMethod.getContainingClass();
        if (siblingClass != null && !siblingClass.equals(containingClass)) {
          classesForGetters.add(siblingClass);
        }
      }
    }
    if (!classesForGetters.isEmpty()) {
      if (getter == null) {
        final PsiMethod newGetter = PropertyUtil.generateGetterPrototype(field);
        getter = newGetter;
        try {
          containingClass.add(newGetter);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      final PsiType returnType = getter.getReturnType();
      assert returnType != null;
      final String methodString = returnType.getCanonicalText() + ' ' + getter.getName() + "();";
      final PsiManager manager = containingClass.getManager();
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      for (PsiClass siblingClass : classesForGetters) {
        if (siblingClass.findMethodBySignature(getter, false) != null) {
          continue;
        }
        if (siblingClass.isInterface()) {
          try {
            siblingClass.add(elementFactory.createMethodFromText(methodString, null));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          try {
            siblingClass.add(elementFactory.createMethodFromText("public abstract " + methodString, null));
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
