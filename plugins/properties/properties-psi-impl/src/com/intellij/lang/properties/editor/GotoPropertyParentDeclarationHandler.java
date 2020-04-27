/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/**
 * @author Dmitry Batkovich
 */
public class GotoPropertyParentDeclarationHandler extends GotoDeclarationHandlerBase {
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
