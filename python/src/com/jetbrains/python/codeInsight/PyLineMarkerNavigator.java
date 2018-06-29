// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    final NavigatablePsiElement[] methods = navElements.toArray(NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      elt.putUserData(MARKERS, methods);
    }
    else {
      PsiElementListNavigator.openTargets(e, methods, getTitle(elt), null, new DefaultPsiElementCellRenderer());
    }
  }

  /**
   * @see #navigate(MouseEvent, PsiElement)
   * @see #MARKERS
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
