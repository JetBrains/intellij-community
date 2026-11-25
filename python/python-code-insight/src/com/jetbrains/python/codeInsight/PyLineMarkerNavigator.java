// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Finds the targets of a gutter icon in a background read action, then navigates to them on the EDT.
 * A key press or a mouse click cancels the search.
 */
@ApiStatus.Internal
public abstract class PyLineMarkerNavigator<T extends PsiElement> implements GutterIconNavigationHandler<T> {

  @Override
  public void navigate(final MouseEvent e, final T elt) {
    final Project project = elt.getProject();
    if (DumbService.isDumb(project)) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"), DumbModeBlockedFunctionality.GotoTarget);
      return;
    }
    new PsiTargetNavigator<NavigatablePsiElement>(() -> findTargets(elt)).navigate(e, getTitle(elt), project);
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  private @NotNull Collection<NavigatablePsiElement> findTargets(final @NotNull T elt) {
    if (!elt.isValid()) {
      return List.of();
    }
    final Query<? extends PsiElement> elementQuery = search(elt, TypeEvalContext.userInitiated(elt.getProject(), elt.getContainingFile()));
    if (elementQuery == null) {
      return List.of();
    }
    final var targets = new LinkedHashSet<NavigatablePsiElement>();
    try {
      elementQuery.forEach(psiElement -> {
        if (psiElement instanceof NavigatablePsiElement navigatable) {
          targets.add(PyiUtil.getNavigationTarget(navigatable, elt) instanceof NavigatablePsiElement original ? original : navigatable);
        }
        return true;
      });
    }
    catch (IndexNotReadyException ignored) {
      // The indexing started after the click. The click then navigates nowhere.
      return List.of();
    }
    return targets;
  }

  protected abstract @PopupTitle String getTitle(T elt);

  protected abstract @Nullable Query<? extends PsiElement> search(T elt, @NotNull TypeEvalContext context);
}
