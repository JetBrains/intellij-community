// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    mavenProject.getSources().forEach(it -> result.add(new Variant(it, JavaSourceRootType.SOURCE)));
    mavenProject.getTestSources().forEach(it -> result.add(new Variant(it, JavaSourceRootType.TEST_SOURCE)));

    mavenProject.getResources().forEach(it -> result.add(new Variant(it.getDirectory(), JavaResourceRootType.RESOURCE)));
    mavenProject.getTestResources().forEach(it -> result.add(new Variant(it.getDirectory(), JavaResourceRootType.TEST_RESOURCE)));

    return result;
  }
}
