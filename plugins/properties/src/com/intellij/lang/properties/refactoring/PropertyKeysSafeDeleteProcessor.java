/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.refactoring;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringSettings;
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

/**
 * @author Dmitry Batkovich
 */
public class PropertyKeysSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {

  @Nullable
  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @Nullable Module module,
                                                              @NotNull Collection<PsiElement> allElementsToDelete) {
    return Collections.singleton(element);
  }

  @Override
  public boolean handlesElement(PsiElement element) {
    return element instanceof IProperty;
  }

  @Nullable
  @Override
  public NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element, @NotNull PsiElement[] allElementsToDelete, @NotNull List<UsageInfo> result) {
    SafeDeleteProcessor.findGenericElementUsages(element, result, allElementsToDelete);
    return new NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(allElementsToDelete), element);
  }

  @Nullable
  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                              @NotNull Collection<PsiElement> allElementsToDelete,
                                                              boolean askUser) {
    final IProperty property = (IProperty)element;
    final String key = property.getKey();
    if (key == null) {
      return null;
    }
    final PropertiesFile file = property.getPropertiesFile();
    if (file == null) {
      return null;
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
    return result;
  }

  @Nullable
  @Override
  public Collection<String> findConflicts(@NotNull PsiElement element, @NotNull PsiElement[] allElementsToDelete) {
    return null;
  }

  @Nullable
  @Override
  public UsageInfo[] preprocessUsages(Project project, UsageInfo[] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(PsiElement element) throws IncorrectOperationException {

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
