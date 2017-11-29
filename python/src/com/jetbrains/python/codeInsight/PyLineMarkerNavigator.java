/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
abstract class PyLineMarkerNavigator<T extends PsiElement> implements GutterIconNavigationHandler<T> {

  private static final Key<NavigatablePsiElement[]> MARKERS = new Key<>("PyLineMarkerNavigatorMarkers");

  @Override
  public void navigate(final MouseEvent e, final T elt) {
    final List<NavigatablePsiElement> navElements = new ArrayList<>();
    final Query<? extends PsiElement> elementQuery = search(elt, TypeEvalContext.userInitiated(elt.getProject(), elt.getContainingFile()));
    if (elementQuery == null) {
      return;
    }
    elementQuery.forEach(psiElement -> {
      if (psiElement instanceof NavigatablePsiElement) {
        navElements.add((NavigatablePsiElement)psiElement);
      }
      return true;
    });
    /**
     * For test purposes, we should be able to access list of methods to check em.
     * {@link PsiElementListNavigator} simply opens then (hence it is swing-based) and can't be used in tests.
     * So, in unit tests we save data in element and data could be obtained with {@link #getNavigationTargets(UserDataHolder)}
     */
    final NavigatablePsiElement[] methods = navElements.toArray(new NavigatablePsiElement[navElements.size()]);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      elt.putUserData(MARKERS, methods);
    }
    else {
      PsiElementListNavigator.openTargets(e, methods, getTitle(elt), null, new DefaultPsiElementCellRenderer());
    }
  }

  /**
   * @see {@link #navigate(MouseEvent, PsiElement)} and {@link #MARKERS}
   */
  @TestOnly
  @Nullable
  static NavigatablePsiElement[] getNavigationTargets(@NotNull final UserDataHolder holder) {
    return holder.getUserData(MARKERS);
  }

  protected abstract String getTitle(T elt);

  @Nullable
  protected abstract Query<? extends PsiElement> search(T elt, @NotNull TypeEvalContext context);
}
