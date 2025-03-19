// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class LanguageLevelQuickFixFactory {

  public static @Nullable LanguageLevelQuickFix getInstance(@NotNull Project project, @NotNull MavenProject mavenProject) {
    return getInstance(project, mavenProject, false);
  }

  public static @Nullable LanguageLevelQuickFix getTargetInstance(@NotNull Project project, @NotNull MavenProject mavenProject) {
    return getInstance(project, mavenProject, true);
  }

  private static @Nullable LanguageLevelQuickFix getInstance(@NotNull Project project, @NotNull MavenProject mavenProject, boolean targetVersion) {
    FixHolder holder = getFixHolder(project, mavenProject, targetVersion);
    if (holder == null) return null;
    return holder.toInstance();
  }

  private static @Nullable FixHolder getFixHolder(@NotNull Project project, @NotNull MavenProject mavenProject, boolean targetVersion) {
    MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return null;
    if (containCompilerPluginSource(model)) {
      return new FixHolder(true, targetVersion, mavenProject, project);
    }
    else if (containPropertySource(model)) {
      return new FixHolder(false, targetVersion, mavenProject, project);
    }
    else if (mavenProject.getParentId() == null) {
      return new FixHolder(false, targetVersion, mavenProject, project);
    }

    mavenProject = getParentMavenProject(project, mavenProject);
    model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return null;
    return containCompilerPluginSource(model)
           ? new FixHolder(true, targetVersion, mavenProject, project) : new FixHolder(false, targetVersion, mavenProject, project);
  }

  private static @NotNull MavenProject getParentMavenProject(@NotNull Project project, @NotNull MavenProject mavenProject) {
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

  private static class FixHolder {
    final boolean isPlugin;
    final boolean isTarget;
    final MavenProject mavenProject;
    final Project project;

    private FixHolder(boolean plugin, boolean target, MavenProject mavenProject, Project project) {
      this.isPlugin = plugin;
      this.isTarget = target;
      this.mavenProject = mavenProject;
      this.project = project;
    }


    private LanguageLevelQuickFix toInstance() {
      if (isPlugin && isTarget) {
        return new TargetLevelPluginQuickFix(project, mavenProject);
      }
      else if (!isPlugin && isTarget) {
        return new TargetLevelPropertyQuickFix(project, mavenProject);
      }
      else if (isPlugin) {
        return new LanguageLevelPluginQuickFix(project, mavenProject);
      }
      else {
        return new LanguageLevelPropertyQuickFix(project, mavenProject);
      }
    }
  }
}
