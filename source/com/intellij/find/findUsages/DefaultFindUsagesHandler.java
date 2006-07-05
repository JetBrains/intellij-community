/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.find.findUsages;

import com.intellij.CommonBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.find.FindBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author peter
 */
public class DefaultFindUsagesHandler extends FindUsagesHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findUsages.DefaultFindUsagesHandler");
  @NonNls private static final String ACTION_STRING = FindBundle.message("find.super.method.warning.action.verb");

  private PsiElement[] myElementsToSearch = null;
  private PsiElement myPsiElement = null;
  private FindUsagesOptions myFindPackageOptions;
  private FindUsagesOptions myFindClassOptions;
  private FindUsagesOptions myFindMethodOptions;
  private FindUsagesOptions myFindVariableOptions;
  private FindUsagesOptions myFindPointcutOptions;
  private final Project myProject;

  public FindUsagesOptions getFindClassOptions() {
    return myFindClassOptions;
  }

  public DefaultFindUsagesHandler(Project project) {
    myProject = project;
    myFindPackageOptions = createFindUsagesOptions(project);
    myFindClassOptions = createFindUsagesOptions(project);
    myFindMethodOptions = createFindUsagesOptions(project);
    myFindVariableOptions = createFindUsagesOptions(project);
    myFindPointcutOptions = createFindUsagesOptions(project);
  }

  public boolean canFindUsages(PsiElement element) {
    if (!super.canFindUsages(element)) {
      return false;
    }

    if (element instanceof PsiDirectory) {
      PsiPackage psiPackage = ((PsiDirectory)element).getPackage();
      if (psiPackage == null) {
        return false;
      }
      element = psiPackage;
    }

    if (element instanceof PsiMethod) {
      final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, ACTION_STRING);
      if (methods.length > 1) {
        myElementsToSearch = methods;
      }
      else if (methods.length == 1) {
        element = methods[0];
      }
      else {
        return false;
      }
    }

    if (element == null) {
      return false;
    }

    myPsiElement = element;
    return true;
  }

  public FindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab,
                                     boolean mustOpenInNewTab) {

    if (myPsiElement instanceof PsiPackage) {
      return new FindPackageUsagesDialog(myPsiElement, myProject, myFindPackageOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    else if (myPsiElement instanceof PsiClass) {
      return new FindClassUsagesDialog(myPsiElement, myProject, myFindClassOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    else if (myPsiElement instanceof PsiMethod) {
      return new FindMethodUsagesDialog(myPsiElement, myProject, myFindMethodOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    else if (myPsiElement instanceof PsiVariable) {
      return new FindVariableUsagesDialog(myPsiElement, myProject, myFindVariableOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    else if (ThrowSearchUtil.isSearchable(myPsiElement)) {
      return new FindThrowUsagesDialog(myPsiElement, myProject, myFindPointcutOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    else {
      return new CommonFindUsagesDialog(myPsiElement, myProject, createFindUsagesOptions(myProject), toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }

  }

  private static FindUsagesOptions createFindUsagesOptions(Project project) {
    FindUsagesOptions findUsagesOptions = new FindUsagesOptions(project);
    findUsagesOptions.isUsages = true;
    findUsagesOptions.isIncludeOverloadUsages = false;
    findUsagesOptions.isIncludeSubpackages = true;
    findUsagesOptions.isReadAccess = true;
    findUsagesOptions.isWriteAccess = true;
    findUsagesOptions.isCheckDeepInheritance = true;
    findUsagesOptions.isSearchForTextOccurences = true;
    return findUsagesOptions;
  }

  private static boolean shouldSearchForParameterInOverridingMethods(final PsiElement psiElement, final PsiParameter parameter) {
    return Messages.showDialog(psiElement.getProject(),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.prompt", parameter.getName()),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.title"),
                               new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0,
                               Messages.getQuestionIcon()) == 0;
  }

  private PsiElement[] getParameterElementsToSearch(final PsiParameter parameter) {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiSearchHelper helper = parameter.getManager().getSearchHelper();
    PsiMethod[] overrides = helper.findOverridingMethods(method, GlobalSearchScope.allScope(myProject), true);
    for (int i = 0; i < overrides.length; i++) {
      overrides[i] = (PsiMethod)overrides[i].getNavigationElement();
    }
    PsiElement[] elementsToSearch = new PsiElement[overrides.length + 1];
    elementsToSearch[0] = parameter;
    int idx = method.getParameterList().getParameterIndex(parameter);
    for (int i = 0; i < overrides.length; i++) {
      elementsToSearch[i + 1] = overrides[i].getParameterList().getParameters()[idx];
    }
    return elementsToSearch;
  }


  @NotNull
  public PsiElement[] getPrimaryElements() {
    LOG.assertTrue(myPsiElement.isValid());
    if (myPsiElement instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)myPsiElement;
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        if (PsiUtil.canBeOverriden(method)) {
          final PsiClass aClass = method.getContainingClass();
          LOG.assertTrue(aClass != null); //Otherwise can not be overriden
          if (aClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT) ||
              shouldSearchForParameterInOverridingMethods(myPsiElement, parameter)) {
            return getParameterElementsToSearch(parameter);
          }
        }
      }
    }
    return myElementsToSearch == null ? new PsiElement[]{myPsiElement} : myElementsToSearch;
  }

  @NotNull
  public PsiElement[] getSecondaryElements() {
    if (myPsiElement instanceof PsiField) {
      final PsiField field = (PsiField)myPsiElement;
      if (field.getContainingClass() != null) {
        final String propertyName =
          field.getManager().getCodeStyleManager().variableNameToPropertyName(field.getName(), VariableKind.FIELD);
        PsiMethod getter = PropertyUtil.
          findPropertyGetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                     ContainerUtil.iterate(field.getContainingClass().getMethods()));
        PsiMethod setter = PropertyUtil.
          findPropertySetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                     ContainerUtil.iterate(field.getContainingClass().getMethods()));
        if (getter != null || setter != null) {
          if (Messages.showDialog(FindBundle.message("find.field.accessors.prompt", field.getName()),
                                  FindBundle.message("find.field.accessors.title"),
                                  new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0,
                                  Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
            final List<PsiElement> elements = new ArrayList<PsiElement>();
            if (getter != null) {
              elements.addAll(Arrays.asList(SuperMethodWarningUtil.checkSuperMethods(getter, ACTION_STRING)));
            }
            if (setter != null) {
              elements.addAll(Arrays.asList(SuperMethodWarningUtil.checkSuperMethods(setter, ACTION_STRING)));
            }
            return elements.toArray(new PsiElement[elements.size()]);
          }
        }
      }
    }
    return super.getSecondaryElements();
  }

}
