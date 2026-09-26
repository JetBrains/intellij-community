// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.navigation;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public class GotoRelatedPropertyDeclarationsProvider extends GotoRelatedProvider {
  @Override
  public @NotNull @Unmodifiable List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(context);
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    if (propertiesFile == null) {
      return Collections.emptyList();
    }

    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    List<PropertiesFile> otherFiles = ContainerUtil.filter(resourceBundle.getPropertiesFiles(), f -> !f.equals(propertiesFile));
    if (otherFiles.isEmpty()) {
      return Collections.emptyList();
    }

    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    if (element == null) {
      final Editor editor = CommonDataKeys.EDITOR.getData(context);
      if (editor != null) {
        int offset = editor.getCaretModel().getOffset();
        element = psiFile.findElementAt(offset);
        if (element == null && offset > 0) {
          element = psiFile.findElementAt(offset - 1);
        }
      }
    }
    final IProperty property = element != null ? PsiTreeUtil.getParentOfType(element, Property.class, false) : null;
    final String propertyKey = property != null ? property.getUnescapedKey() : null;

    return ContainerUtil.map(otherFiles, f -> {
      IProperty found = propertyKey != null ? f.findPropertyByKey(propertyKey) : null;
      if (found != null) {
        return new GotoRelatedItem(found.getPsiElement(), PropertiesBundle.message("goto.property.declaration.group"));
      }
      return new GotoRelatedItem((PsiElement)f, PropertiesBundle.message("goto.other.localizations.group"));
    });
  }
}
