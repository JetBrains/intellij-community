// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public interface XmlTypedHandlersAdditionalSupport {
  ExtensionPointName<XmlTypedHandlersAdditionalSupport> EP_NAME = new ExtensionPointName<>("com.intellij.xml.xmlTypedHandlersAdditionalSupport");

  static boolean supportsTypedHandlers(@NotNull PsiFile psiFile) {
    if (!EP_NAME.hasAnyExtensions()) {
      return false;
    }
    return ContainerUtil.exists(EP_NAME.getExtensionList(), supporter -> {
      for (Language language : psiFile.getViewProvider().getLanguages()) {
        if (supporter.isAvailable(psiFile, language)) return true;
      }
      return false;
    });
  }

  static boolean supportsTypedHandlers(@NotNull PsiFile psiFile, @NotNull Language lang) {
    if (!EP_NAME.hasAnyExtensions()) {
      return false;
    }
    return ContainerUtil.exists(EP_NAME.getExtensionList(), supporter -> supporter.isAvailable(psiFile, lang));
  }
  
  boolean isAvailable(@NotNull PsiFile psiFile, @NotNull Language lang);
}