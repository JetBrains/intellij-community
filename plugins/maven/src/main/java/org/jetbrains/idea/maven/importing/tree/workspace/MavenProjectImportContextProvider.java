// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.MavenJavaVersionHolder;
import org.jetbrains.idea.maven.importing.tree.MavenModuleType;
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.concat;
import static org.jetbrains.idea.maven.importing.MavenModelUtil.*;

public class MavenProjectImportContextProvider {
  @NotNull
  private final Project myProject;
  @NotNull
  private final MavenProjectsTree myProjectsTree;
  @NotNull
  private final Map<MavenId, MavenProjectChanges> myProjectsToImportWithChanges;

  @NotNull
  private final MavenImportingSettings myImportingSettings;

  public MavenProjectImportContextProvider(@NotNull Project project,
                                           @NotNull MavenProjectsTree projectsTree,
                                           @NotNull Map<MavenProject, MavenProjectChanges> changes,
                                           @NotNull MavenImportingSettings importingSettings) {
    myProject = project;
    myProjectsTree = projectsTree;
    myProjectsToImportWithChanges = changes.entrySet().stream()
      .collect(Collectors.toMap(e -> e.getKey().getMavenId(), e -> e.getValue(), (v1, v2) -> v1));
    myImportingSettings = importingSettings;
  }

  public MavenModuleImportContext getContext() {
    ModuleImportDataContext importDataContext = getModuleImportDataContext();
    ModuleImportDataDependecyContext importDataDependencyContext = getFlattenModuleDataDependencyContext(importDataContext);

    return new MavenModuleImportContext(
      importDataDependencyContext.changedModuleDataWithDependencies,
      importDataDependencyContext.allModuleDataWithDependencies,
      importDataContext.moduleNameByProject,
      importDataContext.hasChanges
    );
  }

  @NotNull
  private MavenProjectImportContextProvider.ModuleImportDataContext getModuleImportDataContext() {
    boolean hasChanges = false;
    List<MavenProjectImportData> allModules = new ArrayList<>();
    Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId = new TreeMap<>(Comparator.comparing(MavenId::getKey));
    Map<MavenProject, String> moduleNameByProject = new HashMap<>();

    for (MavenProject project : myProjectsTree.getProjects()) {
      if (myProjectsTree.isIgnored(project)) continue;

      String moduleName = getModuleName(project, myProjectsTree, moduleNameByProject);
      if (StringUtil.isEmpty(moduleName)) {
        MavenLog.LOG.warn("[import context] empty module name for project " + project);
        continue;
      }

      MavenProjectChanges changes = myProjectsToImportWithChanges.get(project.getMavenId());
      MavenProjectImportData mavenProjectImportData = getModuleImportData(project, moduleName, changes);

      if (changes != null && changes.hasChanges()) {
        hasChanges = true;
      }
      moduleImportDataByMavenId.put(project.getMavenId(), mavenProjectImportData);
      allModules.add(mavenProjectImportData);
    }

    return new ModuleImportDataContext(allModules, moduleNameByProject, moduleImportDataByMavenId, hasChanges);
  }

  @NotNull
  private MavenProjectImportContextProvider.ModuleImportDataDependecyContext getFlattenModuleDataDependencyContext(
    ModuleImportDataContext context) {
    List<MavenModuleImportData> allModuleDataWithDependencies = new ArrayList<>();
    List<MavenModuleImportData> changedModuleDataWithDependencies = new ArrayList<>();

    MavenModuleImportDependencyProvider dependencyProvider =
      new MavenModuleImportDependencyProvider(myProject, context.moduleImportDataByMavenId, myImportingSettings, myProjectsTree);

    for (MavenProjectImportData importData : context.importData) {
      MavenModuleImportDataWithDependencies importDataWithDependencies = dependencyProvider.getDependencies(importData);
      List<MavenModuleImportData> mavenModuleImportDataList = splitToModules(importDataWithDependencies);
      for (MavenModuleImportData moduleImportData : mavenModuleImportDataList) {
        if (moduleImportData.hasChanges()) changedModuleDataWithDependencies.add(moduleImportData);
        allModuleDataWithDependencies.add(moduleImportData);
      }
    }

    return new ModuleImportDataDependecyContext(allModuleDataWithDependencies, changedModuleDataWithDependencies);
  }

  @NotNull
  private static List<MavenModuleImportData> splitToModules(MavenModuleImportDataWithDependencies dataWithDependencies) {
    SplittedMainAndTestModules mainAndTestModules = dataWithDependencies.getModuleImportData().splittedMainAndTestModules;
    MavenProject project = dataWithDependencies.getModuleImportData().mavenProject;
    ModuleData moduleData = dataWithDependencies.getModuleImportData().moduleData;
    MavenProjectChanges changes = dataWithDependencies.getModuleImportData().changes;

    if (mainAndTestModules != null) {
      List<MavenModuleImportData> result = new ArrayList<>(3);
      result.add(new MavenModuleImportData(
        project, moduleData, Collections.emptyList(), dataWithDependencies.getModuleImportData().changes
      ));
      result.add(new MavenModuleImportData(
        project, mainAndTestModules.mainData, dataWithDependencies.getMainDependencies(), changes
      ));
      List<MavenImportDependency<?>> dependencies = concat(dataWithDependencies.getTestDependencies(),
                                                           dataWithDependencies.getMainDependencies());
      result.add(new MavenModuleImportData(project, mainAndTestModules.testData, dependencies, changes
      ));
      return result;
    }

    return List.of(new MavenModuleImportData(
      project, moduleData, concat(dataWithDependencies.getMainDependencies(), dataWithDependencies.getTestDependencies()), changes
    ));
  }

  private static MavenProjectImportData getModuleImportData(MavenProject project,
                                                            String moduleName,
                                                            MavenProjectChanges changes) {
    MavenJavaVersionHolder javaVersions = getMavenJavaVersions(project);
    MavenModuleType type = getModuleType(project, javaVersions);

    ModuleData moduleData = getModuleData(project, moduleName, type, javaVersions);
    if (type != MavenModuleType.AGGREGATOR_MAIN_TEST) {
      return new MavenProjectImportData(project, moduleData, changes, null);
    }
    String moduleMainName = moduleName + MAIN_SUFFIX;
    ModuleData mainData = getModuleData(project, moduleMainName, MavenModuleType.MAIN, javaVersions);

    String moduleTestName = moduleName + TEST_SUFFIX;
    ModuleData testData = getModuleData(project, moduleTestName, MavenModuleType.TEST, javaVersions);

    SplittedMainAndTestModules mainAndTestModules = new SplittedMainAndTestModules(mainData, testData);
    return new MavenProjectImportData(project, moduleData, changes, mainAndTestModules);
  }

  private static MavenModuleType getModuleType(MavenProject project, MavenJavaVersionHolder mavenJavaVersions) {
    if (needSplitMainAndTest(project, mavenJavaVersions)) {
      return MavenModuleType.AGGREGATOR_MAIN_TEST;
    }
    else if (project.isAggregator()) {
      return MavenModuleType.AGGREGATOR;
    }
    else {
      return MavenModuleType.MAIN_TEST;
    }
  }

  private static boolean needSplitMainAndTest(MavenProject project, MavenJavaVersionHolder mavenJavaVersions) {
    return !project.isAggregator() && mavenJavaVersions.needSeparateTestModule() && isCompilerTestSupport(project);
  }

  private static ModuleData getModuleData(MavenProject project, String moduleName,
                                          MavenModuleType type,
                                          MavenJavaVersionHolder javaVersionHolder) {
    return new ModuleData(moduleName, getModuleDirPath(project, type), type, javaVersionHolder);
  }

  private static String getModuleDirPath(MavenProject project, MavenModuleType type) {
    if (type == MavenModuleType.TEST) {
      return Path.of(project.getDirectory(), "src", "test").toString();
    }
    if (type == MavenModuleType.MAIN) {
      return Path.of(project.getDirectory(), "src", "main").toString();
    }
    return project.getDirectory();
  }

  private static class ModuleImportDataContext {
    @NotNull final List<MavenProjectImportData> importData;
    @NotNull final Map<MavenProject, String> moduleNameByProject;
    @NotNull final Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId;
    final boolean hasChanges;

    private ModuleImportDataContext(@NotNull List<MavenProjectImportData> importData,
                                    @NotNull Map<MavenProject, String> moduleNameByProject,
                                    @NotNull Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId,
                                    boolean hasChanges) {
      this.importData = importData;
      this.moduleNameByProject = moduleNameByProject;
      this.moduleImportDataByMavenId = moduleImportDataByMavenId;
      this.hasChanges = hasChanges;
    }
  }

  private static class ModuleImportDataDependecyContext {
    @NotNull final List<MavenModuleImportData> allModuleDataWithDependencies;
    @NotNull final List<MavenModuleImportData> changedModuleDataWithDependencies;

    private ModuleImportDataDependecyContext(@NotNull List<MavenModuleImportData> allModuleDataWithDependencies,
                                             @NotNull List<MavenModuleImportData> changedModuleDataWithDependencies) {
      this.allModuleDataWithDependencies = allModuleDataWithDependencies;
      this.changedModuleDataWithDependencies = changedModuleDataWithDependencies;
    }
  }


  static class MavenProjectImportData {
    @NotNull final MavenProject mavenProject;
    @NotNull final ModuleData moduleData;
    @Nullable final MavenProjectChanges changes;
    @Nullable final SplittedMainAndTestModules splittedMainAndTestModules;

    MavenProjectImportData(@NotNull MavenProject mavenProject,
                           @NotNull ModuleData moduleData,
                           @Nullable MavenProjectChanges changes,
                           @Nullable SplittedMainAndTestModules splittedMainAndTestModules) {
      this.mavenProject = mavenProject;
      this.changes = changes;
      this.moduleData = moduleData;
      this.splittedMainAndTestModules = splittedMainAndTestModules;
    }

    public boolean hasChanges() {
      return changes != null && changes.hasChanges();
    }

    @Override
    public String toString() {
      return mavenProject.getMavenId().toString();
    }
  }

  static class SplittedMainAndTestModules {
    @NotNull final ModuleData mainData;
    @NotNull final ModuleData testData;

    SplittedMainAndTestModules(@NotNull ModuleData mainData,
                               @NotNull ModuleData testData) {
      this.mainData = mainData;
      this.testData = testData;
    }
  }
}
