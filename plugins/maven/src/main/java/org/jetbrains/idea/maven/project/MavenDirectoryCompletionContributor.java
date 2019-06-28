// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.CreateDirectoryCompletionContributor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MavenDirectoryCompletionContributor implements CreateDirectoryCompletionContributor {
  @NotNull
  @Override
  public String getDescription() {
    return "Maven Source Directories";
  }

  @NotNull
  @Override
  public Collection<Variant> getVariants(@NotNull PsiDirectory directory) {
    Project project = directory.getProject();

    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(directory.getVirtualFile());
    if (module == null) return Collections.emptyList();

    MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
    if (!mavenManager.isMavenizedModule(module)) return Collections.emptyList();

    MavenProject mavenProject = mavenManager.findProject(module);
    if (mavenProject == null) return Collections.emptyList();

    List<Variant> result = new ArrayList<>();
    for (String each : mavenProject.getSources()) {
      result.add(new Variant(each, AllIcons.Modules.SourceRoot));
    }
    for (String each : mavenProject.getTestSources()) {
      result.add(new Variant(each, AllIcons.Modules.TestRoot));
    }

    return result;
  }
}
