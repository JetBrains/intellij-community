// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.concat;
import static org.jetbrains.idea.maven.importing.MavenModelUtil.*;

public class MavenProjectImportContextProvider {
  @NotNull
  protected final Project myProject;
  @NotNull
  protected final MavenProjectsTree myProjectsTree;
  @NotNull
  protected final Map<MavenId, MavenProjectChanges> myProjectsToImportWithChanges;
  @NotNull
  protected final MavenImportingSettings myImportingSettings;

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
      importDataContext.hasChanges,

      importDataDependencyContext.legacyCreatedModules,
      importDataContext.legacyObsoleteModules
    );
  }

  @NotNull
  private MavenProjectImportContextProvider.ModuleImportDataContext getModuleImportDataContext() {
    boolean hasChanges = false;
    List<MavenProjectImportData> allModules = new ArrayList<>();
    Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId = new TreeMap<>(Comparator.comparing(MavenId::getKey));
    Map<MavenProject, String> moduleNameByProject = new HashMap<>();

    Map<String, Module> legacyModuleByName = buildModuleByNameMap();

    for (MavenProject project : myProjectsTree.getProjects()) {
      if (myProjectsTree.isIgnored(project)) continue;

      String moduleName = getModuleName(project, myProjectsTree, moduleNameByProject);
      if (StringUtil.isEmpty(moduleName)) {
        MavenLog.LOG.warn("[import context] empty module name for project " + project);
        continue;
      }

      MavenProjectChanges changes = myProjectsToImportWithChanges.get(project.getMavenId());
      MavenProjectImportData mavenProjectImportData = getModuleImportData(project, moduleName, legacyModuleByName, changes);

      if (changes != null && changes.hasChanges()) {
        hasChanges = true;
      }
      moduleImportDataByMavenId.put(project.getMavenId(), mavenProjectImportData);
      allModules.add(mavenProjectImportData);
    }

    return new ModuleImportDataContext(allModules, moduleNameByProject, moduleImportDataByMavenId,
                                       new ArrayList<>(legacyModuleByName.values()), hasChanges);
  }

  protected Map<String, Module> buildModuleByNameMap() {
    return Collections.emptyMap();
  }

  @NotNull
  private MavenProjectImportContextProvider.ModuleImportDataDependecyContext getFlattenModuleDataDependencyContext(
    ModuleImportDataContext context) {
    List<Module> legacyCreatedModules = new ArrayList<>();
    List<MavenModuleImportData> allModuleDataWithDependencies = new ArrayList<>();
    List<MavenModuleImportData> changedModuleDataWithDependencies = new ArrayList<>();

    MavenModuleImportDependencyProvider dependencyProvider =
      new MavenModuleImportDependencyProvider(myProject, context.moduleImportDataByMavenId, myImportingSettings, myProjectsTree);

    for (MavenProjectImportData importData : context.importData) {
      MavenModuleImportDataWithDependencies importDataWithDependencies = dependencyProvider.getDependencies(importData);
      List<MavenModuleImportData> mavenModuleImportDataList = splitToModules(importDataWithDependencies);
      for (MavenModuleImportData moduleImportData : mavenModuleImportDataList) {
        if (moduleImportData.hasChanges()) changedModuleDataWithDependencies.add(moduleImportData);

        addLegacyCreatedModule(legacyCreatedModules, moduleImportData);

        allModuleDataWithDependencies.add(moduleImportData);
      }
    }

    return new ModuleImportDataDependecyContext(allModuleDataWithDependencies, changedModuleDataWithDependencies, legacyCreatedModules);
  }

  protected void addLegacyCreatedModule(List<Module> createdModules, MavenModuleImportData moduleImportData) {
  }

  @NotNull
  protected static List<MavenModuleImportData> splitToModules(MavenModuleImportDataWithDependencies dataWithDependencies) {
    SplittedMainAndTestModules mainAndTestModules = dataWithDependencies.getModuleImportData().getSplittedMainAndTestModules();
    MavenProject project = dataWithDependencies.getModuleImportData().getMavenProject();
    ModuleData moduleData = dataWithDependencies.getModuleImportData().getModuleData();
    MavenProjectChanges changes = dataWithDependencies.getModuleImportData().getChanges();

    if (mainAndTestModules != null) {
      List<MavenModuleImportData> result = new ArrayList<>(3);
      result.add(new MavenModuleImportData(
        project, moduleData, Collections.emptyList(), dataWithDependencies.getModuleImportData().getChanges()
      ));
      result.add(new MavenModuleImportData(
        project, mainAndTestModules.getMainData(), dataWithDependencies.getMainDependencies(), changes
      ));
      List<MavenImportDependency<?>> dependencies = concat(dataWithDependencies.getTestDependencies(),
                                                           dataWithDependencies.getMainDependencies());
      result.add(new MavenModuleImportData(project, mainAndTestModules.getTestData(), dependencies, changes
      ));
      return result;
    }

    return List.of(new MavenModuleImportData(
      project, moduleData, concat(dataWithDependencies.getMainDependencies(), dataWithDependencies.getTestDependencies()), changes
    ));
  }

  protected MavenProjectImportData getModuleImportData(MavenProject project,
                                                       String moduleName,
                                                       Map<String, Module> legacyModuleByName,
                                                       MavenProjectChanges changes) {
    MavenJavaVersionHolder javaVersions = getMavenJavaVersions(project);
    MavenModuleType type = getModuleType(project, javaVersions);

    ModuleData moduleData = getModuleData(project, moduleName, type, javaVersions, legacyModuleByName);
    if (type != MavenModuleType.AGGREGATOR_MAIN_TEST) {
      return new MavenProjectImportData(project, moduleData, changes, null);
    }
    String moduleMainName = moduleName + MAIN_SUFFIX;
    ModuleData mainData = getModuleData(project, moduleMainName, MavenModuleType.MAIN, javaVersions, legacyModuleByName);

    String moduleTestName = moduleName + TEST_SUFFIX;
    ModuleData testData = getModuleData(project, moduleTestName, MavenModuleType.TEST, javaVersions, legacyModuleByName);

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

  protected ModuleData getModuleData(MavenProject project, String moduleName,
                                     MavenModuleType type,
                                     MavenJavaVersionHolder javaVersionHolder,
                                     Map<String, Module> legacyModuleByName) {
    return new ModuleData(moduleName, type, javaVersionHolder);
  }

  private static class ModuleImportDataContext {
    @NotNull final List<MavenProjectImportData> importData;
    @NotNull final Map<MavenProject, String> moduleNameByProject;
    @NotNull final Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId;
    @NotNull final List<Module> legacyObsoleteModules;
    final boolean hasChanges;

    private ModuleImportDataContext(@NotNull List<MavenProjectImportData> importData,
                                    @NotNull Map<MavenProject, String> moduleNameByProject,
                                    @NotNull Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId,
                                    @NotNull List<Module> legacyObsoleteModules,
                                    boolean hasChanges) {
      this.importData = importData;
      this.moduleNameByProject = moduleNameByProject;
      this.moduleImportDataByMavenId = moduleImportDataByMavenId;
      this.legacyObsoleteModules = legacyObsoleteModules;
      this.hasChanges = hasChanges;
    }
  }

  private static class ModuleImportDataDependecyContext {
    @NotNull final List<MavenModuleImportData> allModuleDataWithDependencies;
    @NotNull final List<MavenModuleImportData> changedModuleDataWithDependencies;
    @NotNull final List<Module> legacyCreatedModules;

    private ModuleImportDataDependecyContext(@NotNull List<MavenModuleImportData> allModuleDataWithDependencies,
                                             @NotNull List<MavenModuleImportData> changedModuleDataWithDependencies,
                                             @NotNull List<Module> legacyCreatedModules) {
      this.allModuleDataWithDependencies = allModuleDataWithDependencies;
      this.changedModuleDataWithDependencies = changedModuleDataWithDependencies;
      this.legacyCreatedModules = legacyCreatedModules;
    }
  }
}


