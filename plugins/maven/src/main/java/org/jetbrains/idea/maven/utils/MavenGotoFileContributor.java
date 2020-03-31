// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Objects;

public class MavenGotoFileContributor implements ChooseByNameContributorEx {
  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    Project project = Objects.requireNonNull(scope.getProject());
    for (MavenProject p : MavenProjectsManager.getInstance(project).getProjects()) {
      String id = p.getMavenId().getArtifactId();
      if (id != null && !processor.process(id)) return;
    }
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
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
