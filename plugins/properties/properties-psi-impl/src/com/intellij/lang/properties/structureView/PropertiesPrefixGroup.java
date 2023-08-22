// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class PropertiesPrefixGroup implements Group {
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
