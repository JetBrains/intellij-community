/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class FindUsagesHandler {
  private final PsiElement myPsiElement;

  protected FindUsagesHandler(final PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @NotNull
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    return new CommonFindUsagesDialog(myPsiElement, getProject(), createFindUsagesOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile);
  }

  public final PsiElement getPsiElement() {
    return myPsiElement;
  }

  protected final Project getProject() {
    return myPsiElement.getManager().getProject();
  }

  @NotNull
  public PsiElement[] getPrimaryElements() {
    return new PsiElement[]{myPsiElement};
  }

  @NotNull
  public PsiElement[] getSecondaryElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  protected FindUsagesOptions createFindUsagesOptions() {
    return createFindUsagesOptions(getProject());
  }

  public static FindUsagesOptions createFindUsagesOptions(final Project project) {
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
}
