/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.find.findUsages;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class DefaultFindUsagesHandler extends FindUsagesHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findUsages.DefaultFindUsagesHandler");
  @NonNls static final String ACTION_STRING = FindBundle.message("find.super.method.warning.action.verb");

  private final PsiElement[] myElementsToSearch;
  private final FindUsagesOptions myFindPackageOptions;
  private final FindUsagesOptions myFindClassOptions;
  private final FindUsagesOptions myFindMethodOptions;
  private final FindUsagesOptions myFindVariableOptions;
  private final FindUsagesOptions myFindPointcutOptions;

  public DefaultFindUsagesHandler(final PsiElement psiElement,
                                final FindUsagesOptions findClassOptions,
                                final FindUsagesOptions findMethodOptions,
                                final FindUsagesOptions findPackageOptions,
                                final FindUsagesOptions findPointcutOptions,
                                final FindUsagesOptions findVariableOptions) {
    this(psiElement, null, findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
  }


  public DefaultFindUsagesHandler(final PsiElement psiElement, final PsiElement[] elementsToSearch,
                                  final FindUsagesOptions findClassOptions,
                                  final FindUsagesOptions findMethodOptions,
                                  final FindUsagesOptions findPackageOptions,
                                  final FindUsagesOptions findPointcutOptions,
                                  final FindUsagesOptions findVariableOptions) {
    super(psiElement);
    myElementsToSearch = elementsToSearch;
    myFindClassOptions = findClassOptions;
    myFindMethodOptions = findMethodOptions;
    myFindPackageOptions = findPackageOptions;
    myFindPointcutOptions = findPointcutOptions;
    myFindVariableOptions = findVariableOptions;
  }

  @NotNull
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab,
                                     boolean mustOpenInNewTab) {
    PsiElement element = getPsiElement();
    if (element instanceof PsiPackage) {
      return new FindPackageUsagesDialog(element, getProject(), myFindPackageOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    if (element instanceof PsiClass) {
      return new FindClassUsagesDialog(element, getProject(), myFindClassOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    if (element instanceof PsiMethod) {
      return new FindMethodUsagesDialog(element, getProject(), myFindMethodOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    if (element instanceof PsiVariable) {
      return new FindVariableUsagesDialog(element, getProject(), myFindVariableOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return new FindThrowUsagesDialog(element, getProject(), myFindPointcutOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile);
    }
    return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab);
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
    PsiMethod[] overrides = helper.findOverridingMethods(method, GlobalSearchScope.allScope(getProject()), true);
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
    final PsiElement element = getPsiElement();
    if (element instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)element;
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        if (PsiUtil.canBeOverriden(method)) {
          final PsiClass aClass = method.getContainingClass();
          LOG.assertTrue(aClass != null); //Otherwise can not be overriden
          if (aClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT) ||
              shouldSearchForParameterInOverridingMethods(element, parameter)) {
            return getParameterElementsToSearch(parameter);
          }
        }
      }
    }
    return myElementsToSearch == null ? new PsiElement[]{element} : myElementsToSearch;
  }

  @NotNull
  public PsiElement[] getSecondaryElements() {
    PsiElement element = getPsiElement();
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
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
