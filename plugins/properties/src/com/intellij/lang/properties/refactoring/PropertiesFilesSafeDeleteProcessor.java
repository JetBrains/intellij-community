// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.refactoring;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class PropertiesFilesSafeDeleteProcessor implements SafeDeleteProcessorDelegate {
  @Override
  public boolean handlesElement(final PsiElement element) {
    return element instanceof PropertiesFile;
  }

  @Override
  public NonCodeUsageSearchInfo findUsages(@NotNull final PsiElement element, final PsiElement @NotNull [] allElementsToDelete, final @NotNull List<? super UsageInfo> result) {
    PropertiesFile file = (PropertiesFile) element;
    List<PsiElement> elements = new ArrayList<>();
    elements.add(file.getContainingFile());
    for (IProperty property : file.getProperties()) {
      elements.add(property.getPsiElement());
    }
    for(PsiElement psiElement: elements) {
      SafeDeleteProcessor.findGenericElementUsages(psiElement, result, allElementsToDelete, GlobalSearchScope.projectScope(element.getProject()));
    }
    return new NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(allElementsToDelete), elements);
  }

  @Override
  public Collection<PsiElement> getElementsToSearch(@NotNull final PsiElement element, final @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    return Collections.singletonList(element);
  }

  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull final PsiElement element, final @NotNull Collection<? extends PsiElement> allElementsToDelete,
                                                              final boolean askUser) {
    return null;
  }

  @Override
  public Collection<String> findConflicts(@NotNull final PsiElement element, final PsiElement @NotNull [] allElementsToDelete) {
    return null;
  }

  @Override
  public UsageInfo[] preprocessUsages(final @NotNull Project project, final UsageInfo @NotNull [] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(final @NotNull PsiElement element) throws IncorrectOperationException {
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS = enabled;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA = enabled;
  }
}
