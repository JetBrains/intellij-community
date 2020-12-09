// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

final class GotoPropertyParentDeclarationHandler extends GotoDeclarationHandlerBase {
  @Nullable
  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, Editor editor) {
    Property property = findProperty(sourceElement);
    if (property == null) return null;
    final String key = property.getKey();
    if (key == null) return null;
    PropertiesFile currentFile = PropertiesImplUtil.getPropertiesFile(property.getContainingFile());
    if (currentFile == null) return null;

    do {
      currentFile = PropertiesUtil.getParent(currentFile, currentFile.getResourceBundle().getPropertiesFiles());
      if (currentFile != null) {
        final IProperty parent = currentFile.findPropertyByKey(key);
        if (parent != null) return parent.getPsiElement();
      } else {
        return null;
      }
    }
    while (true);
  }

  static Property findProperty(@Nullable PsiElement source) {
    if (source == null) return null;
    if (source instanceof Property) return (Property)source;
    final PsiElement parent = source.getParent();
    return parent instanceof Property ? (Property)parent : null;
  }
}
