// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

import javax.swing.*;
import java.util.Objects;

public class YAMLKeyNavigationItem implements NavigationItem {
  private final @NotNull Navigatable myNavigatable;
  private final @NotNull String myName;
  private final @NotNull VirtualFile myFile;
  private final int myPosition;

  YAMLKeyNavigationItem(@NotNull Project project, @NotNull String name, @NotNull VirtualFile file, int position) {
    myNavigatable = PsiNavigationSupport.getInstance().createNavigatable(project, file, position);
    myName = name;
    myFile = file;
    myPosition = position;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    YAMLKeyNavigationItem item = (YAMLKeyNavigationItem)o;
    return myPosition == item.myPosition && myName.equals(item.myName) && myFile.equals(item.myFile);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myFile, myPosition);
  }
}
