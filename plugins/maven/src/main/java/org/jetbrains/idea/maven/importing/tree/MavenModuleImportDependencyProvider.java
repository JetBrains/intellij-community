// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Pair;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.dependency.*;
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;

import java.util.*;

import static org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter.*;

public class MavenModuleImportDependencyProvider {
  public static final int INITIAL_CAPACITY_TEST_DEPENDENCY_LIST = 4;

  @NotNull private final Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId;
  @NotNull private final Set<String> dependencyTypesFromSettings;
  @NotNull private final MavenProjectsTree myProjectTree;

  public MavenModuleImportDependencyProvider(@NotNull Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId,
                                             @NotNull MavenImportingSettings importingSettings,
                                             @NotNull MavenProjectsTree projectTree) {
    this.moduleImportDataByMavenId = moduleImportDataByMavenId;
    this.dependencyTypesFromSettings = importingSettings.getDependencyTypesAsSet();
    myProjectTree = projectTree;
  }

  @NotNull
  public MavenModuleImportDataWithDependencies getDependencies(MavenProjectImportData importData) {
    MavenProject mavenProject = importData.getMavenProject();
    List<MavenImportDependency<?>> mainDependencies = new ArrayList<>(mavenProject.getDependencies().size());
    List<MavenImportDependency<?>> testDependencies = new ArrayList<>(INITIAL_CAPACITY_TEST_DEPENDENCY_LIST);

    addMainDependencyToTestModule(importData, testDependencies);
    boolean hasSeparateTestModule = importData.getSplittedMainAndTestModules() != null;
    for (MavenArtifact artifact : mavenProject.getDependencies()) {
      for (MavenImportDependency<?> dependency : getDependency(artifact, mavenProject)) {
        if (hasSeparateTestModule && dependency.getScope() == DependencyScope.TEST) {
          testDependencies.add(dependency);
        }
        else {
          mainDependencies.add(dependency);
        }
      }
    }
    return new MavenModuleImportDataWithDependencies(importData, mainDependencies, testDependencies);
  }

  private static void addMainDependencyToTestModule(MavenProjectImportData importData,
                                                    List<MavenImportDependency<?>> testDependencies) {
    if (importData.getSplittedMainAndTestModules() != null) {
      testDependencies.add(
        new ModuleDependency(importData.getSplittedMainAndTestModules().getMainData().getModuleName(), DependencyScope.COMPILE, false)
      );
    }
  }

  @NotNull
  private List<MavenImportDependency<?>> getDependency(MavenArtifact artifact, MavenProject mavenProject) {
    String dependencyType = artifact.getType();

    if (!dependencyTypesFromSettings.contains(dependencyType)
        && !mavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType)) {
      return Collections.emptyList();
    }

    DependencyScope scope = selectScope(artifact.getScope());

    MavenProject depProject = myProjectTree.findProject(artifact.getMavenId());

    if (depProject != null) {
      if (depProject == mavenProject) return Collections.emptyList();

      MavenProjectImportData mavenProjectImportData = moduleImportDataByMavenId.get(depProject.getMavenId());

      if (mavenProjectImportData == null || myProjectTree.isIgnored(depProject)) {
        return List.of(new BaseDependency(createCopyForLocalRepo(artifact, mavenProject), scope));
      }
      else {
        var result = new ArrayList<MavenImportDependency<?>>();
        boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(dependencyType) || "tests".equals(artifact.getClassifier());
        String moduleName = getModuleName(mavenProjectImportData, isTestJar);

        ContainerUtil.addIfNotNull(result, createAttachArtifactDependency(depProject, scope, artifact));

        String classifier = artifact.getClassifier();
        if (classifier != null && IMPORTED_CLASSIFIERS.contains(classifier)
            && !isTestJar
            && !"system".equals(artifact.getScope())
            && !"false".equals(System.getProperty("idea.maven.classifier.dep"))) {
          result.add(new LibraryDependency(createCopyForLocalRepo(artifact, mavenProject), mavenProject, scope));
        }

        result.add(new ModuleDependency(moduleName, scope, isTestJar));
        return result;
      }
    }
    else if ("system".equals(artifact.getScope())) {
      return List.of(new SystemDependency(artifact, scope));
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
      return List.of(new BaseDependency(artifact, scope));
    }
  }

  private static String getModuleName(MavenProjectImportData data, boolean isTestJar) {
    SplittedMainAndTestModules modules = data.getSplittedMainAndTestModules();
    if (modules == null) {
      return data.getModuleData().getModuleName();
    }
    return isTestJar ? modules.getTestData().getModuleName() : modules.getMainData().getModuleName();
  }

  @Nullable
  private static AttachedJarDependency createAttachArtifactDependency(@NotNull MavenProject mavenproject,
                                                                      @NotNull DependencyScope scope,
                                                                      @NotNull MavenArtifact artifact) {
    Element buildHelperCfg = mavenproject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact");
    if (buildHelperCfg == null) return null;

    var roots = new ArrayList<Pair<String, LibraryRootTypeId>>();
    var create = false;

    for (Element artifactsElement : buildHelperCfg.getChildren("artifacts")) {
      for (Element artifactElement : artifactsElement.getChildren("artifact")) {
        String typeString = artifactElement.getChildTextTrim("type");
        if (typeString != null && !typeString.equals("jar")) continue;

        String filePath = artifactElement.getChildTextTrim("file");
        if (StringUtil.isEmpty(filePath)) continue;

        String classifier = artifactElement.getChildTextTrim("classifier");
        if ("sources".equals(classifier)) {
          roots.add(new Pair<>(filePath, LibraryRootTypeId.Companion.getSOURCES()));
        }
        else if ("javadoc".equals(classifier)) {
          roots.add(new Pair<>(filePath, WorkspaceModuleImporter.Companion.getJAVADOC_TYPE()));
        }
        else {
          roots.add(new Pair<>(filePath, LibraryRootTypeId.Companion.getCOMPILED()));
        }

        create = true;
      }
    }

    return create ? new AttachedJarDependency(getAttachedJarsLibName(artifact), roots, scope) : null;
  }
}
