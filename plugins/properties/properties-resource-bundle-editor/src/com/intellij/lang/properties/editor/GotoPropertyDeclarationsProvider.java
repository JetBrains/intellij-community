// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class GotoPropertyDeclarationsProvider extends GotoRelatedProvider {

  @Override
  public @NotNull @Unmodifiable List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final FileEditor editor = PlatformCoreDataKeys.FILE_EDITOR.getData(context);
    if (!(editor instanceof ResourceBundleEditor resourceBundleEditor)) {
      return Collections.emptyList();
    }
    final Collection<ResourceBundleEditorViewElement> elements = resourceBundleEditor.getSelectedElements();
    if (elements.size() != 1) {
      return Collections.emptyList();
    }
    final IProperty[] properties = ContainerUtil.getFirstItem(elements).getProperties();
    if (properties == null || properties.length != 1 || !(properties[0] instanceof Property)) {
      return Collections.emptyList();
    }
    final IProperty property = properties[0];
    final String propertyKey = property.getKey();
    final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(property.getPsiElement().getContainingFile());
    assert file != null;
    final ResourceBundle resourceBundle = file.getResourceBundle();
    return ContainerUtil.mapNotNull(resourceBundle.getPropertiesFiles(), f -> {
      final IProperty foundProperty = f.findPropertyByKey(propertyKey);
      return foundProperty == null ?
             null :
             new GotoRelatedItem(foundProperty.getPsiElement(), ResourceBundleEditorBundle.message("goto.property.declaration.group"));
    });
  }
}