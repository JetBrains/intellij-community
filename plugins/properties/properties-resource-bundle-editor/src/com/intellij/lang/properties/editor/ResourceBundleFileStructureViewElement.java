// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.properties.editor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class ResourceBundleFileStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  @NotNull
  private final ResourceBundle myResourceBundle;
  @NotNull
  private final BooleanSupplier myGrouped;

  private volatile boolean myShowOnlyIncomplete;
  private final Map<String, PropertyStructureViewElement> myElements = new LinkedHashMap<>();

  public ResourceBundleFileStructureViewElement(@NotNull ResourceBundle resourceBundle, @NotNull BooleanSupplier grouped) {
    myResourceBundle = resourceBundle;
    myGrouped = grouped;
  }

  public void setShowOnlyIncomplete(boolean showOnlyIncomplete) {
    myShowOnlyIncomplete = showOnlyIncomplete;
  }

  public boolean isShowOnlyIncomplete() {
    return myShowOnlyIncomplete;
  }

  @Override
  public ResourceBundle getValue() {
    return myResourceBundle.isValid() ? myResourceBundle : null;
  }

  @Override
  public synchronized StructureViewTreeElement @NotNull [] getChildren() {
    final MultiMap<String, IProperty> propertyNames = getPropertiesMap(myResourceBundle, myShowOnlyIncomplete);

    final HashSet<String> remains = new HashSet<>(myElements.keySet());
    for (Map.Entry<String, Collection<IProperty>> entry : propertyNames.entrySet()) {
      final String propKey = entry.getKey();
      Collection<IProperty> properties = entry.getValue();
      final PropertyStructureViewElement oldPropertyNode = myElements.get(propKey);
      if (oldPropertyNode != null && properties.contains(oldPropertyNode.getProperty())) {
        remains.remove(propKey);
        continue;
      }
      if (myElements.containsKey(propKey)) {
        remains.remove(propKey);
      }
      final IProperty representative = properties.iterator().next();
      myElements.put(propKey, new PropertyBundleEditorStructureViewElement(representative, myGrouped));
    }

    for (String remain : remains) {
      myElements.remove(remain);
    }

    return myElements.values().toArray(StructureViewTreeElement.EMPTY_ARRAY);
  }

  public static MultiMap<String, IProperty> getPropertiesMap(ResourceBundle resourceBundle, boolean onlyIncomplete) {
    if (!resourceBundle.isValid()) {
      return MultiMap.empty();
    }
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    final MultiMap<String, IProperty> propertyNames;
    if (onlyIncomplete) {
      propertyNames = getChildrenIdShowOnlyIncomplete(resourceBundle);
    }
    else {
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
    Object2IntMap<String> occurrences=new Object2IntOpenHashMap<>();
    for (PropertiesFile file : resourceBundle.getPropertiesFiles()) {
      MultiMap<String, IProperty> currentFilePropertyNames = MultiMap.createLinked();
      for (IProperty property : file.getProperties()) {
        String name = property.getKey();
        currentFilePropertyNames.putValue(name, property);
      }
      propertyNames.putAllValues(currentFilePropertyNames);
      for (String propertyName : currentFilePropertyNames.keySet()) {
        occurrences.mergeInt(propertyName, 1, Math::addExact);
      }
    }
    final int targetOccurrences = resourceBundle.getPropertiesFiles().size();
    for (Object2IntMap.Entry<String> entry : occurrences.object2IntEntrySet()) {
      if (entry.getIntValue() == targetOccurrences) {
        propertyNames.remove(entry.getKey());
      }
    }
    return propertyNames;
  }

  @Override
  public IProperty @NotNull [] getProperties() {
    return IProperty.EMPTY_ARRAY;
  }

  @Override
  public PsiFile @Nullable [] getFiles() {
    ResourceBundle rb = getValue();
    if (rb == null) return null;
    final List<PropertiesFile> files = rb.getPropertiesFiles();
    return ContainerUtil.map2Array(files, new PsiFile[files.size()], propertiesFile -> propertiesFile.getContainingFile());
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return myResourceBundle.isValid() ? myResourceBundle.getBaseName() : null;
      }

      @Override
      public Icon getIcon(boolean open) {
        return AllIcons.FileTypes.Properties;
      }
    };
  }

  @Override
  public void navigate(boolean requestFocus) {

  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }
}
