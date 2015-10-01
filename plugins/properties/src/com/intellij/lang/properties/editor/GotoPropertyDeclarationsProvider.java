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
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class GotoPropertyDeclarationsProvider extends GotoRelatedProvider {

  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (!(editor instanceof ResourceBundleEditor)) {
      return Collections.emptyList();
    }
    final ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)editor;
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
    return ContainerUtil.mapNotNull(resourceBundle.getPropertiesFiles(), new NullableFunction<PropertiesFile, GotoRelatedItem>() {
      @Nullable
      @Override
      public GotoRelatedItem fun(PropertiesFile f) {
        final IProperty foundProperty = f.findPropertyByKey(propertyKey);
        return foundProperty == null ?
               null :
               new GotoRelatedItem(foundProperty.getPsiElement(), "Property Declarations");
      }
    });
  }
}
