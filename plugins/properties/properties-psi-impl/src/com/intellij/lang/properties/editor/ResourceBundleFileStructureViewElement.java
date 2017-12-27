/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.lang.properties.editor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ResourceBundleFileStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private final ResourceBundle myResourceBundle;

  private volatile boolean myShowOnlyIncomplete;
  private final Map<String, ResourceBundlePropertyStructureViewElement> myElements = ContainerUtil.newLinkedHashMap();

  public ResourceBundleFileStructureViewElement(final ResourceBundle resourceBundle) {
    myResourceBundle = resourceBundle;
  }

  public void setShowOnlyIncomplete(boolean showOnlyIncomplete) {
    myShowOnlyIncomplete = showOnlyIncomplete;
  }

  public boolean isShowOnlyIncomplete() {
    return myShowOnlyIncomplete;
  }

  @Override
  public ResourceBundle getValue() {
    return myResourceBundle.isValid() ? null : myResourceBundle;
  }

  @NotNull
  public synchronized StructureViewTreeElement[] getChildren() {
    final MultiMap<String, IProperty> propertyNames = getPropertiesMap(myResourceBundle, myShowOnlyIncomplete);

    final HashSet<String> remains = new HashSet<>(myElements.keySet());
    for (Map.Entry<String, Collection<IProperty>> entry : propertyNames.entrySet()) {
      final String propKey = entry.getKey();
      Collection<IProperty> properties = entry.getValue();
      final ResourceBundlePropertyStructureViewElement oldPropertyNode = myElements.get(propKey);
      if (oldPropertyNode != null && properties.contains(oldPropertyNode.getProperty())) {
        remains.remove(propKey);
        continue;
      }
      if (myElements.containsKey(propKey)) {
        remains.remove(propKey);
      }
      final IProperty representative = properties.iterator().next();
      myElements.put(propKey, new ResourceBundlePropertyStructureViewElement(representative));
    }

    for (String remain : remains) {
      myElements.remove(remain);
    }

    return myElements.values().toArray(StructureViewTreeElement.EMPTY_ARRAY);
  }

  public static MultiMap<String, IProperty> getPropertiesMap(ResourceBundle resourceBundle, boolean onlyIncomplete) {
    if (!resourceBundle.isValid()) {
      //noinspection unchecked
      return MultiMap.EMPTY;
    }
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
        return myResourceBundle.isValid() ? myResourceBundle.getBaseName() : null;
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
    return IProperty.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public PsiFile[] getFiles() {
    ResourceBundle rb = getValue();
    if (rb == null) return null;
    final List<PropertiesFile> files = rb.getPropertiesFiles();
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
