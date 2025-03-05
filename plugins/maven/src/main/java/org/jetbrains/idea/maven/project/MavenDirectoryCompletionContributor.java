// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.ide.actions.CreateDirectoryCompletionContributor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class MavenDirectoryCompletionContributor implements CreateDirectoryCompletionContributor {
  @Override
  public @NotNull String getDescription() {
    return MavenProjectBundle.message("maven.directory.contribution.description");
  }

  @Override
  public @NotNull Collection<Variant> getVariants(@NotNull PsiDirectory directory) {
    Project project = directory.getProject();

    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(directory.getVirtualFile());
    if (module == null) return Collections.emptyList();

    MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
    if (!mavenManager.isMavenizedModule(module)) return Collections.emptyList();

    MavenProject mavenProject = mavenManager.findProject(module);
    if (mavenProject == null) return Collections.emptyList();

    List<Variant> result = new ArrayList<>();

    mavenProject.getSources().forEach(it -> result.add(new Variant(it, JavaSourceRootType.SOURCE)));
    mavenProject.getTestSources().forEach(it -> result.add(new Variant(it, JavaSourceRootType.TEST_SOURCE)));

    mavenProject.getResources().forEach(it -> result.add(new Variant(it.getDirectory(), JavaResourceRootType.RESOURCE)));
    mavenProject.getTestResources().forEach(it -> result.add(new Variant(it.getDirectory(), JavaResourceRootType.TEST_RESOURCE)));

    return result;
  }
}
