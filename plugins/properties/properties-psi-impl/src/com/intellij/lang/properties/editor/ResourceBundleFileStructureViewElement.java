/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ResourceBundleFileStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private final ResourceBundle myResourceBundle;

  private boolean myShowOnlyIncomplete;
  private final PropertiesAnchorizer myAnchorizer;

  public ResourceBundleFileStructureViewElement(final ResourceBundle resourceBundle, PropertiesAnchorizer anchorizer) {
    myResourceBundle = resourceBundle;
    myAnchorizer = anchorizer;
  }

  public void setShowOnlyIncomplete(boolean showOnlyIncomplete) {
    myShowOnlyIncomplete = showOnlyIncomplete;
  }

  public boolean isShowOnlyIncomplete() {
    return myShowOnlyIncomplete;
  }

  @Override
  public ResourceBundle getValue() {
    return myResourceBundle;
  }

  @NotNull
  public StructureViewTreeElement[] getChildren() {
    final MultiMap<String, IProperty> propertyNames = getPropertiesMap(myResourceBundle, myShowOnlyIncomplete);
    List<StructureViewTreeElement> result = new ArrayList<>(propertyNames.size());
    for (Map.Entry<String, Collection<IProperty>> entry : propertyNames.entrySet()) {
      final Collection<IProperty> properties = entry.getValue();
      final PropertiesAnchorizer.PropertyAnchor anchor = myAnchorizer.createOrUpdate(properties);
      result.add(new ResourceBundlePropertyStructureViewElement(myResourceBundle, anchor));
    }
    return result.toArray(new StructureViewTreeElement[result.size()]);
  }

  public static MultiMap<String, IProperty> getPropertiesMap(ResourceBundle resourceBundle, boolean onlyIncomplete) {
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    final MultiMap<String, IProperty> propertyNames;
    if (onlyIncomplete) {
      propertyNames = getChildrenIdShowOnlyIncomplete(resourceBundle);
    } else {
      propertyNames = MultiMap.createLinked();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        List<IProperty> properties = propertiesFile.getProperties();
        for (IProperty property : properties) {
          String name = property.getKey();
          propertyNames.putValue(name, property);
        }
      }
    }
    return propertyNames;
  }

  private static MultiMap<String, IProperty> getChildrenIdShowOnlyIncomplete(ResourceBundle resourceBundle) {
    final MultiMap<String, IProperty> propertyNames = MultiMap.createLinked();
    TObjectIntHashMap<String> occurrences = new TObjectIntHashMap<>();
    for (PropertiesFile file : resourceBundle.getPropertiesFiles()) {
      MultiMap<String, IProperty> currentFilePropertyNames = MultiMap.createLinked();
      for (IProperty property : file.getProperties()) {
        String name = property.getKey();
        currentFilePropertyNames.putValue(name, property);
      }
      propertyNames.putAllValues(currentFilePropertyNames);
      for (String propertyName : currentFilePropertyNames.keySet()) {
        if (occurrences.contains(propertyName)) {
          occurrences.adjustValue(propertyName, 1);
        }
        else {
          occurrences.put(propertyName, 1);
        }
      }
    }
    final int targetOccurrences = resourceBundle.getPropertiesFiles().size();
    occurrences.forEachEntry(new TObjectIntProcedure<String>() {
      @Override
      public boolean execute(String propertyName, int occurrences) {
        if (occurrences == targetOccurrences) {
          propertyNames.remove(propertyName);
        }
        return true;
      }
    });
    return propertyNames;
  }

  @NotNull
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return myResourceBundle.getBaseName();
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return AllIcons.FileTypes.Properties;
      }
    };
  }

  @Nullable
  @Override
  public IProperty[] getProperties() {
    return new IProperty[0];
  }

  @Nullable
  @Override
  public PsiFile[] getFiles() {
    final List<PropertiesFile> files = getValue().getPropertiesFiles();
    return ContainerUtil.map2Array(files, new PsiFile[files.size()], propertiesFile -> propertiesFile.getContainingFile());
  }

  public void navigate(boolean requestFocus) {

  }

  public boolean canNavigate() {
    return false;
  }

  public boolean canNavigateToSource() {
    return false;
  }
}
