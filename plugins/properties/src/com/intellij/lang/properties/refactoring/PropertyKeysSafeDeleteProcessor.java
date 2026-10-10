// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.refactoring;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class PropertyKeysSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {
  /** It asks nothing: the keys of the other files of the bundle go without a question. */
  @Override
  public @NotNull ModPlanSafeDeleteProvider asModPlan() {
    return new ModPlanLegacySafeDeleteProvider(this);
  }

  @Override
  public @NotNull Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                                       @Nullable Module module,
                                                                       @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    return Collections.singleton(element);
  }

  @Override
  public boolean handlesElement(PsiElement element) {
    return element instanceof IProperty;
  }

  @Override
  public @NotNull NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element, PsiElement @NotNull [] allElementsToDelete, @NotNull List<? super UsageInfo> result) {
    SafeDeleteProcessor.findGenericElementUsages(element, result, allElementsToDelete);
    return new NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(allElementsToDelete), element);
  }

  @Override
  public @NotNull AdditionalElementsData getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                                       @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    final IProperty property = (IProperty)element;
    final String key = property.getKey();
    if (key == null) {
      return AdditionalElementsData.NONE;
    }
    final PropertiesFile file = property.getPropertiesFile();
    if (file == null) {
      return AdditionalElementsData.NONE;
    }

    final List<PsiElement> result = new ArrayList<>();
    for (PropertiesFile propertiesFile : file.getResourceBundle().getPropertiesFiles()) {
      for (IProperty p : propertiesFile.findPropertiesByKey(key)) {
        final PsiElement propertyElement = p.getPsiElement();
        if (!allElementsToDelete.contains(propertyElement)) {
          result.add(propertyElement);
        }
      }
    }
    return new AdditionalElementsData(result);
  }

  @Override
  public UsageInfo @Nullable [] preprocessUsages(@NotNull Project project, UsageInfo @NotNull [] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(@NotNull PsiElement element) throws IncorrectOperationException {

  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA = enabled;
  }
}
