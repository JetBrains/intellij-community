// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class RenamePyClassProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PyClass;
  }

  @Override
  public boolean isToSearchInComments(@NotNull PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
  }

  @Override
  public void setToSearchInComments(@NotNull PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_CLASS;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_CLASS = enabled;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(@NotNull final PsiElement element) {
    if (element instanceof PyClass) {
      final PyFunction initMethod = ((PyClass)element).findMethodByName(PyNames.INIT, true, null);
      if (initMethod != null) {
        final List<PsiReference> allRefs = Collections.synchronizedList(new ArrayList<PsiReference>());
        allRefs.addAll(super.findReferences(element));
        ReferencesSearch.search(initMethod, GlobalSearchScope.projectScope(element.getProject())).forEach(psiReference -> {
          if (psiReference.getCanonicalText().equals(((PyClass)element).getName())) {
            allRefs.add(psiReference);
          }
          return true;
        });
        return allRefs;
      }
    }
    return super.findReferences(element);
  }
}
