/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTemplatesUtil {
  private PyTemplatesUtil(){}
  
  public static ValidationResult checkInstalled(@Nullable final Sdk sdk, @NotNull final TemplateLanguagePanel templatesPanel,
                                         @NotNull final String prefix) {
    if (sdk == null) return ValidationResult.OK;
    String templateBinding = null;
    @NonNls String language = templatesPanel.getTemplateLanguage();
    if (language != null) {
      if (language.equals(TemplatesService.JINJA2)) language = "jinja";
      templateBinding = prefix + language.toLowerCase();
    }
    final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
    if (templateBinding != null) {
      if (TemplatesService.ALL_TEMPLATE_BINDINGS.contains(templateBinding)) {
        try {
          final PyPackage installedPackage = packageManager.findInstalledPackage(templateBinding);
          if (installedPackage == null)
            return new ValidationResult(templateBinding + " will be installed on selected interpreter");
        }
        catch (PyExternalProcessException ignored) {
        }
      }
    }
    if (language != null) {
      try {
        final PyPackage installedPackage = packageManager.findInstalledPackage(language);
        if (installedPackage == null) {
          return new ValidationResult(language + " will be installed on selected interpreter");
        }
      }
      catch (PyExternalProcessException ignored) {
      }
    }
    return null;
  }

}

