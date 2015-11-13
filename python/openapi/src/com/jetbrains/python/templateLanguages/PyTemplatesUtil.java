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
package com.jetbrains.python.templateLanguages;

import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.lang.Language;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTemplatesUtil {
  private PyTemplatesUtil() {
  }

  public static ValidationResult checkInstalled(@Nullable final Sdk sdk, @NotNull final TemplateLanguagePanel templatesPanel,
                                                @NotNull final String prefix) {
    if (sdk == null) return ValidationResult.OK;
    String templateBinding = null;
    @NonNls String language = templatesPanel.getTemplateLanguage();
    if (language != null) {
      String postfix = language.toLowerCase();
      if (language.equals(TemplatesService.JINJA2)) {
        postfix = "jinja";
      }
      templateBinding = prefix + postfix;
    }
    final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
    if (templateBinding != null) {
      if (TemplatesService.ALL_TEMPLATE_BINDINGS.contains(templateBinding)) {
        try {
          final PyPackage installedPackage = packageManager.findPackage(templateBinding, false);
          if (installedPackage == null) {
            return new ValidationResult(templateBinding + " will be installed on selected interpreter");
          }
        }
        catch (ExecutionException ignored) {
        }
      }
    }
    if (language != null) {
      try {
        final PyPackage installedPackage = packageManager.findPackage(language, false);
        if (installedPackage == null) {
          return new ValidationResult(language + " will be installed on selected interpreter");
        }
      }
      catch (ExecutionException ignored) {
      }
    }
    return null;
  }

  /**
   * Fetches template data language if file has {@link TemplateLanguageFileViewProvider}
   *
   * @param psiElement       element to get lang for
   * @param expectedProvider only fetch language if provider has certain type. Pass null for any type.
   * @return template data language
   */
  @Nullable
  public static Language getTemplateDataLanguage(@Nullable final PsiElement psiElement,
                                                 @Nullable final Class<? extends TemplateLanguageFileViewProvider> expectedProvider) {
    if (psiElement == null) {
      return null;
    }

    final FileViewProvider provider = psiElement.getContainingFile().getViewProvider();
    if (provider instanceof TemplateLanguageFileViewProvider) {
      if (expectedProvider == null || expectedProvider.isInstance(provider)) {
        return (((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage());
      }
    }

    return psiElement.getLanguage();
  }
}

