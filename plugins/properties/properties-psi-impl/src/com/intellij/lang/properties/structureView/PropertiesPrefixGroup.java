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
package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.editor.ResourceBundleEditorViewElement;
import com.intellij.lang.properties.editor.PropertyStructureViewElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PropertiesPrefixGroup implements Group, ResourceBundleEditorViewElement {
  private final @NotNull Collection<TreeElement> myProperties;
  private final @NotNull String myPrefix;
  private final @NotNull String myPresentableName;
  private final @NotNull String mySeparator;

  public PropertiesPrefixGroup(@NotNull Collection<TreeElement> properties,
                               @NotNull String prefix,
                               @NotNull String presentableName,
                               @NotNull String separator) {
    myProperties = properties;
    myPrefix = prefix;
    myPresentableName = presentableName;
    mySeparator = separator;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull
  public String getSeparator() {
    return mySeparator;
  }

  @NotNull
  public String getPrefix() {
    return myPrefix;
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return myPresentableName;
      }

      @Override
      public String getLocationString() {
        return null;
      }

      @Override
      public Icon getIcon(boolean open) {
        return AllIcons.Nodes.Tag;
      }
    };
  }

  @Override
  @NotNull
  public Collection<TreeElement> getChildren() {
    return myProperties;
  }

  @NotNull
  @Override
  public IProperty[] getProperties() {
    final List<IProperty> elements = ContainerUtil.mapNotNull(getChildren(), (NullableFunction<TreeElement, IProperty>)treeElement -> {
      if (treeElement instanceof PropertyStructureViewElement) {
        return ((PropertyStructureViewElement)treeElement).getProperties()[0];
      }
      return null;
    });
    return elements.toArray(IProperty.EMPTY_ARRAY);
  }

  @Nullable
  @Override
  public PsiFile[] getFiles() {
    return null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PropertiesPrefixGroup group = (PropertiesPrefixGroup)o;

    if (!myPrefix.equals(group.myPrefix)) return false;

    return true;
  }

  public int hashCode() {
    return myPrefix.hashCode();
  }
}
