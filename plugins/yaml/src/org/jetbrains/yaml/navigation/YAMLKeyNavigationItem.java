// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

import javax.swing.*;

class YAMLKeyNavigationItem implements NavigationItem {
  @NotNull
  private final Navigatable myNavigatable;
  @NotNull
  private final String myName;
  @NotNull
  private final VirtualFile myFile;

  public YAMLKeyNavigationItem(@NotNull Navigatable navigatable, @NotNull String name, @NotNull VirtualFile file) {
    myNavigatable = navigatable;
    myName = name;
    myFile = file;
  }

  @Override
  public void navigate(boolean requestFocus) {
    myNavigatable.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @NotNull
      @Override
      public String getPresentableText() {
        return myName;
      }

      @NotNull
      @Override
      public String getLocationString() {
        return myFile.toString();
      }

      @NotNull
      @Override
      public Icon getIcon(boolean unused) {
        return YAMLKeyValueImpl.YAML_KEY_ICON;
      }
    };
  }
}
