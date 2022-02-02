// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.dependency.BaseDependency;
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency;
import org.jetbrains.idea.maven.importing.tree.dependency.ModuleDependency;
import org.jetbrains.idea.maven.importing.tree.dependency.SystemDependency;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.SupportedRequestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.idea.maven.importing.MavenModuleImporter.*;

public class MavenModuleImportDependencyProvider {
  @NotNull private final Project project;
  @NotNull private final Map<MavenId, MavenProjectImportContextProvider.MavenProjectImportData> moduleImportDataByMavenId;
  @NotNull private final Set<String> dependencyTypesFromSettings;

  public MavenModuleImportDependencyProvider(@NotNull Project project,
                                             @NotNull Map<MavenId, MavenProjectImportContextProvider.MavenProjectImportData> moduleImportDataByMavenId,
                                             @NotNull MavenImportingSettings importingSettings) {
    this.project = project;
    this.moduleImportDataByMavenId = moduleImportDataByMavenId;
    this.dependencyTypesFromSettings = importingSettings.getDependencyTypesAsSet();
  }

  @NotNull
  public MavenModuleImportDataWithDependencies getDependencies(MavenProjectImportContextProvider.MavenProjectImportData importData) {
    MavenProject mavenProject = importData.mavenProject;
    List<MavenImportDependency<?>> mainDependencies = new ArrayList<>(mavenProject.getDependencies().size());
    List<MavenImportDependency<?>> testDependencies = new ArrayList<>(5);

    addMainDependencyToTestModule(importData, mavenProject, testDependencies);
    for (MavenArtifact artifact : mavenProject.getDependencies()) {
      MavenImportDependency<?> dependency = getDependency(artifact, mavenProject);
      if (dependency == null) continue;
      if (dependency.getScope() == DependencyScope.TEST) {
        testDependencies.add(dependency);
      }
      else {
        mainDependencies.add(dependency);
      }
    }
    return new MavenModuleImportDataWithDependencies(importData, mainDependencies, testDependencies);
  }

  private static void addMainDependencyToTestModule(MavenProjectImportContextProvider.MavenProjectImportData importData,
                                                    MavenProject mavenProject,
                                                    List<MavenImportDependency<?>> testDependencies) {
    if (importData.splittedMainAndTestModules != null) {
      testDependencies.add(
        new ModuleDependency(null, mavenProject,
                             importData.splittedMainAndTestModules.mainData.getModuleName(),
                             DependencyScope.COMPILE, false)
      );
    }
  }

  @Nullable
  private MavenImportDependency<?> getDependency(MavenArtifact artifact, MavenProject mavenProject) {
    String dependencyType = artifact.getType();

    if (!dependencyTypesFromSettings.contains(dependencyType)
        && !mavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType)) {
      return null;
    }

    DependencyScope scope = selectScope(artifact.getScope());

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    MavenProject depProject = projectsManager.findProject(artifact.getMavenId());

    if (depProject != null) {
      if (depProject == mavenProject) return null;

      MavenProjectImportContextProvider.MavenProjectImportData mavenProjectImportData = moduleImportDataByMavenId.get(depProject.getMavenId());

      if (mavenProjectImportData == null || projectsManager.isIgnored(depProject)) {
        return new BaseDependency(createCopyForLocalRepo(artifact, mavenProject), scope);
      }
      else {
        boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(dependencyType) || "tests".equals(artifact.getClassifier());
        String moduleName = getModuleName(mavenProjectImportData);

        MavenArtifact a = null;
        String classifier = artifact.getClassifier();
        if (classifier != null && IMPORTED_CLASSIFIERS.contains(classifier)
            && !isTestJar
            && !"system".equals(artifact.getScope())
            && !"false".equals(System.getProperty("idea.maven.classifier.dep"))) {
          a = createCopyForLocalRepo(artifact, mavenProject);
        }
        return new ModuleDependency(a, mavenProject, moduleName, scope, isTestJar);
      }
    }
    else if ("system".equals(artifact.getScope())) {
      return new SystemDependency(artifact, scope);
    }
    else {
      if ("bundle".equals(dependencyType)) {
        artifact = new MavenArtifact(
          artifact.getGroupId(),
          artifact.getArtifactId(),
          artifact.getVersion(),
          artifact.getBaseVersion(),
          "jar",
          artifact.getClassifier(),
          artifact.getScope(),
          artifact.isOptional(),
          "jar",
          null,
          mavenProject.getLocalRepository(),
          false, false
        );
      }
      return new BaseDependency(artifact, scope);
    }
  }

  private static String getModuleName(MavenProjectImportContextProvider.MavenProjectImportData data) {
    MavenProjectImportContextProvider.SplittedMainAndTestModules modules = data.splittedMainAndTestModules;
    return modules == null ? data.moduleData.getModuleName() : modules.mainData.getModuleName();
  }
}
