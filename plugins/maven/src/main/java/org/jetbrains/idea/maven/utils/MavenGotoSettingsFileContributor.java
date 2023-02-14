// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MavenGotoSettingsFileContributor implements ChooseByNameContributor, DumbAware {
  @Override
  public String @NotNull [] getNames(Project project, boolean includeNonProjectItems) {
    if (!includeNonProjectItems) return ArrayUtilRt.EMPTY_STRING_ARRAY;

    Set<String> result = new HashSet<>();
    for (VirtualFile each : getSettingsFiles(project)) {
      result.add(each.getName());
    }
    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public NavigationItem @NotNull [] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    if (!includeNonProjectItems) return NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY;

    List<NavigationItem> result = new ArrayList<>();
    for (VirtualFile each : getSettingsFiles(project)) {
      if (each.getName().equals(name)) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(each);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return result.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
  }

  private static List<VirtualFile> getSettingsFiles(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings().getEffectiveSettingsFiles();
  }
}