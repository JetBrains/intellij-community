/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MavenGotoSettingsFileContributor implements ChooseByNameContributor, DumbAware {
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    if (!includeNonProjectItems) return ArrayUtil.EMPTY_STRING_ARRAY;

    Set<String> result = new THashSet<>();
    for (VirtualFile each : getSettingsFiles(project)) {
      result.add(each.getName());
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    if (!includeNonProjectItems) return NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY;

    List<NavigationItem> result = new ArrayList<>();
    for (VirtualFile each : getSettingsFiles(project)) {
      if (each.getName().equals(name)) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(each);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return result.toArray(new NavigationItem[result.size()]);
  }

  private static List<VirtualFile> getSettingsFiles(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings().getEffectiveSettingsFiles();
  }
}