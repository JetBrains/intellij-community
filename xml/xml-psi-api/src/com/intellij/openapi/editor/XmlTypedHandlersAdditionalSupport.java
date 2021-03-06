// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface XmlTypedHandlersAdditionalSupport {
  ExtensionPointName<XmlTypedHandlersAdditionalSupport> EP_NAME = new ExtensionPointName<>("com.intellij.xml.xmlTypedHandlersAdditionalSupport");

  static boolean supportsTypedHandlers(@NotNull PsiFile psiFile) {
    return EP_NAME.hasAnyExtensions() && EP_NAME.extensions().anyMatch(supporter -> {
      for (Language language : psiFile.getViewProvider().getLanguages()) {
        if (supporter.isAvailable(psiFile, language)) return true;
      }
      return false;
    });
  }

  static boolean supportsTypedHandlers(@NotNull PsiFile psiFile, @NotNull Language lang) {
    return EP_NAME.hasAnyExtensions() && EP_NAME.extensions().anyMatch(supporter -> supporter.isAvailable(psiFile, lang));
  }
  
  boolean isAvailable(@NotNull PsiFile psiFile, @NotNull Language lang);
}