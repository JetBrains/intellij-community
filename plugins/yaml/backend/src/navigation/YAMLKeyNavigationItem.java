// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

import javax.swing.*;
import java.util.Objects;

public class YAMLKeyNavigationItem implements NavigationItem {
  private final @NotNull Navigatable myNavigatable;
  private final @NotNull Project myProject;
  private final @NotNull String myName;
  private final @NotNull VirtualFile myFile;
  private final int myPosition;
  private final @NotNull @NlsSafe String myLocation;

  YAMLKeyNavigationItem(@NotNull Project project,
                        @NotNull String name,
                        @NotNull VirtualFile file,
                        int position,
                        @NotNull @NlsSafe String location) {
    myNavigatable = PsiNavigationSupport.getInstance().createNavigatable(project, file, position);
    myProject = project;
    myName = name;
    myFile = file;
    myPosition = position;
    myLocation = location;
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

  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public @NotNull String getPresentableText() {
        return myName;
      }

      @Override
      public @NotNull String getLocationString() {
        return myLocation;
      }

      @Override
      public @NotNull Icon getIcon(boolean unused) {
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
