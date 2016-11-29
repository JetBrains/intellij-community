/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;

public class MavenGotoFileContributor implements ChooseByNameContributor {
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    List<String> result = new ArrayList<>();

    for (MavenProject each : MavenProjectsManager.getInstance(project).getProjects()) {
      result.add(each.getMavenId().getArtifactId());
    }

    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    List<NavigationItem> result = new ArrayList<>();

    for (final MavenProject each : MavenProjectsManager.getInstance(project).getProjects()) {
      if (name.equals(each.getMavenId().getArtifactId())) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(each.getFile());
        if (psiFile != null) result.add(psiFile);
      }
    }

    return result.toArray(new NavigationItem[result.size()]);
  }
}
