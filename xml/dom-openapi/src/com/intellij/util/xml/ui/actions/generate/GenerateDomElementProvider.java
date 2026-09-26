// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.ui.actions.generate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomElementsNavigationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GenerateDomElementProvider<T extends DomElement> {
  private final @ActionDescription String myDescription;
  private final @ActionText String myText;

  public GenerateDomElementProvider(@ActionText String text, @ActionDescription String description) {
    myText = text;
    myDescription = description;
  }

  public boolean isAvailableForElement(@NotNull DomElement contextElement) {
    return true;
  }
  
  public abstract @Nullable T generate(final Project project, final Editor editor, final PsiFile file);

  public void navigate(final DomElement element) {
    if (element != null && element.isValid()) {
      final DomElement copy = element.createStableCopy();
      final Project project = element.getManager().getProject();
      final DomElementNavigationProvider navigateProvider = getNavigationProviderName(project);

      if (navigateProvider != null && navigateProvider.canNavigate(copy)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!project.isDisposed()) {
            doNavigate(navigateProvider, copy);
          }
        });
      }
    }
  }

  protected void doNavigate(final DomElementNavigationProvider navigateProvider, final DomElement copy) {
    navigateProvider.navigate(copy, true);
  }

  protected static DomElementNavigationProvider getNavigationProviderName(Project project) {
    return DomElementsNavigationManager.getManager(project)
      .getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME);
  }

  public @ActionDescription String getDescription() {
    return myDescription == null ? "" : myDescription;
  }

  public @ActionText String getText() {
    return myText == null ? "" : myText;
  }
}

