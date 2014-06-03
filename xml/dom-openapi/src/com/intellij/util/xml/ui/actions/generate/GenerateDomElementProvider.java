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

package com.intellij.util.xml.ui.actions.generate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomElementsNavigationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
public abstract class GenerateDomElementProvider<T extends DomElement> {
  private final String myDescription;

  public GenerateDomElementProvider(String description) {
    myDescription = description;
  }

  public boolean isAvailableForElement(@NotNull DomElement contextElement) {
    return true;
  }
  
  @Nullable
  public abstract T generate(final Project project, final Editor editor, final PsiFile file);

  public void navigate(final DomElement element) {
    if (element != null && element.isValid()) {
      final DomElement copy = element.createStableCopy();
      final Project project = element.getManager().getProject();
      final DomElementNavigationProvider navigateProvider = getNavigationProviderName(project);

      if (navigateProvider != null && navigateProvider.canNavigate(copy)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!project.isDisposed()) {
              doNavigate(navigateProvider, copy);
            }
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

  public String getDescription() {
    return myDescription == null ? "" : myDescription;
  }
}
