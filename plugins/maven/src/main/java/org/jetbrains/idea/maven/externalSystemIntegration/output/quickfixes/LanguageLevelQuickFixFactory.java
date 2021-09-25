// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Optional;

public class LanguageLevelQuickFixFactory {

  @Nullable
  public static LanguageLevelQuickFix getInstance(@NotNull Project project, @NotNull MavenProject mavenProject) {
    MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return null;
    if (containCompilerPluginSource(model)) {
      return new LanguageLevelPluginQuickFix(project, mavenProject);
    }
    else if (containPropertySource(model)) {
      return new LanguageLevelPropertyQuickFix(project, mavenProject);
    }
    else if (mavenProject.getParentId() == null) {
      return new LanguageLevelPropertyQuickFix(project, mavenProject);
    }

    mavenProject = getParentMavenProject(project, mavenProject);
    model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return null;
    return containCompilerPluginSource(model)
           ? new LanguageLevelPluginQuickFix(project, mavenProject) : new LanguageLevelPropertyQuickFix(project, mavenProject);
  }

  @NotNull
  private static MavenProject getParentMavenProject(@NotNull Project project, @NotNull MavenProject mavenProject) {
    MavenProject parentProject = mavenProject;
    while (parentProject.getParentId() != null) {
      parentProject = MavenProjectsManager.getInstance(project).findProject(parentProject.getParentId());
      if (parentProject == null) return mavenProject;
    }
    return parentProject;
  }

  private static boolean containCompilerPluginSource(@NotNull MavenDomProjectModel model) {
    return Optional.ofNullable(MavenProjectModelModifier.findCompilerPlugin(model))
      .map(p -> p.getConfiguration())
      .map(c -> c.getXmlTag())
      .map(tag -> tag.findFirstSubTag(LanguageLevelPluginQuickFix.COMPILER_SOURCE) != null)
      .orElse(false);
  }

  private static boolean containPropertySource(@NotNull MavenDomProjectModel model) {
    return Optional.ofNullable(model.getProperties().getXmlTag())
      .map(tag -> tag.findFirstSubTag(LanguageLevelPropertyQuickFix.MAVEN_COMPILER_SOURCE) != null)
      .orElse(false);
  }
}
