/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ide.util.SuperMethodWarningUtil;

/**
 * @author peter
*/
public class JavaFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  private final FindUsagesOptions myFindClassOptions;
  private final FindUsagesOptions myFindMethodOptions;
  private final FindUsagesOptions myFindPackageOptions;
  private final FindUsagesOptions myFindThrowOptions;                   
  private final FindUsagesOptions myFindVariableOptions;

  public JavaFindUsagesHandlerFactory(Project project) {
    final FindUsagesOptions findClassOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findMethodOptions = FindUsagesHandler.createFindUsagesOptions(project);
    findMethodOptions.isCheckDeepInheritance = false;
    findMethodOptions.isIncludeSubpackages = false;
    findMethodOptions.isSearchForTextOccurences = false;
    final FindUsagesOptions findPackageOptions = FindUsagesHandler.createFindUsagesOptions(project);

    final FindUsagesOptions findThrowOptions = FindUsagesHandler.createFindUsagesOptions(project);
    findThrowOptions.isSearchForTextOccurences = false;
    findThrowOptions.isThrowUsages = true;

    final FindUsagesOptions findVariableOptions = FindUsagesHandler.createFindUsagesOptions(project);
    findVariableOptions.isCheckDeepInheritance = false;
    findVariableOptions.isIncludeSubpackages = false;
    findVariableOptions.isSearchForTextOccurences = false;

    myFindClassOptions = findClassOptions;
    myFindMethodOptions = findMethodOptions;
    myFindPackageOptions = findPackageOptions;
    myFindThrowOptions = findThrowOptions;
    myFindVariableOptions = findVariableOptions;
  }

  public boolean canFindUsages(final PsiElement element) {
    return true;
  }

  public FindUsagesHandler createFindUsagesHandler(final PsiElement element, final boolean forHighlightUsages) {
    if (element instanceof PsiDirectory) {
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      return psiPackage == null
             ? null
             : new JavaFindUsagesHandler(psiPackage, myFindClassOptions, myFindMethodOptions, myFindPackageOptions, myFindThrowOptions,
                                         myFindVariableOptions);
    }

    if (element instanceof PsiMethod && !forHighlightUsages) {
      final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, JavaFindUsagesHandler.ACTION_STRING);
      if (methods.length > 1) {
        return new JavaFindUsagesHandler(element, methods, myFindClassOptions, myFindMethodOptions, myFindPackageOptions,
                                         myFindThrowOptions, myFindVariableOptions);
      }
      if (methods.length == 1) {
        return new JavaFindUsagesHandler(methods[0], myFindClassOptions, myFindMethodOptions, myFindPackageOptions, myFindThrowOptions,
                                         myFindVariableOptions);
      }
      return null;
    }

    return new JavaFindUsagesHandler(element, myFindClassOptions, myFindMethodOptions, myFindPackageOptions, myFindThrowOptions,
                                     myFindVariableOptions);
  }
}
