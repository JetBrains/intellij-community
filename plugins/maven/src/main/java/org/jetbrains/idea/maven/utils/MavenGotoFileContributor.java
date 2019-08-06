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

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenGotoFileContributor implements ChooseByNameContributorEx {
  @Override
  public void processNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = ObjectUtils.notNull(scope.getProject());
    for (MavenProject p : MavenProjectsManager.getInstance(project).getProjects()) {
      String id = p.getMavenId().getArtifactId();
      if (id != null && !processor.process(id)) return;
    }
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    PsiManager psiManager = PsiManager.getInstance(parameters.getProject());
    for (MavenProject each : MavenProjectsManager.getInstance(parameters.getProject()).getProjects()) {
      if (!name.equals(each.getMavenId().getArtifactId())) continue;

      VirtualFile file = each.getFile();
      if (!parameters.getSearchScope().contains(file)) continue;

      PsiFile psiFile = psiManager.findFile(file);
      if (psiFile != null && !processor.process(psiFile)) return;
    }
  }
}
