/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * An extension point allowing plugins to request support for language features
 * not normally present in a given {@link LanguageLevel}.
 * <p>
 * e.g. type annotations in python 2.7.
 */
public interface PyCustomLanguageSupportProvider {

  ExtensionPointName<PyCustomLanguageSupportProvider> EP_NAME =
    new ExtensionPointName<>("Pythonid.customLanguageSupportProvider");

  /**
   * Custom language features, intended to override the default features
   * of the current {@link LanguageLevel}
   */
  class CustomLanguageSupport {

    private static CustomLanguageSupport noCustomFeatures(LanguageLevel languageLevel) {
      boolean supportsFunctionAnnotations = languageLevel.isAtLeast(LanguageLevel.PYTHON30);
      boolean supportsEllipsisLiteral = languageLevel.isAtLeast(LanguageLevel.PYTHON30);
      return new CustomLanguageSupport(supportsFunctionAnnotations, supportsEllipsisLiteral);
    }

    /**
     * Whether type annotations (excluding variable annotations) are supported for this custom language level.
     * <p>
     * <a href=https://www.python.org/dev/peps/pep-3107>PEP-3107</a>
     */
    public final boolean supportsFunctionAnnotations;
    public final boolean supportsEllipsisLiteral;

    public CustomLanguageSupport(boolean supportsFunctionAnnotations, boolean supportsEllipsisLiteral) {
      this.supportsFunctionAnnotations = supportsFunctionAnnotations;
      this.supportsEllipsisLiteral = supportsEllipsisLiteral;
    }
  }

  /**
   * Returns any custom language features to be applied to the language level of this element.
   */
  static CustomLanguageSupport customLanguageSupport(PsiElement psiElement) {
    return customLanguageSupport(psiElement.getProject(), LanguageLevel.forElement(psiElement));
  }

  /**
   * Returns any custom language features to be applied to this language level.
   */
  static CustomLanguageSupport customLanguageSupport(Project project, LanguageLevel languageLevel) {
    for (PyCustomLanguageSupportProvider provider : EP_NAME.getExtensions()) {
      CustomLanguageSupport customSupport = provider.customFeaturesForLanguageLevel(project, languageLevel);
      if (customSupport != null) {
        return customSupport;
      }
    }
    return CustomLanguageSupport.noCustomFeatures(languageLevel);
  }

  CustomLanguageSupport customFeaturesForLanguageLevel(Project project, LanguageLevel languageLevel);
}
