// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.StandardMavenModuleType;
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.concat;
import static org.jetbrains.idea.maven.importing.MavenImportUtil.*;

public class MavenProjectImportContextProvider {
  @NotNull
  protected final Project myProject;
  @NotNull
  protected final MavenProjectsTree myProjectsTree;
  @NotNull
  protected final MavenImportingSettings myImportingSettings;
  @NotNull
  protected final HashMap<MavenProject, String> myMavenProjectToModuleName;

  public MavenProjectImportContextProvider(@NotNull Project project,
                                           @NotNull MavenProjectsTree projectsTree,
                                           @NotNull MavenImportingSettings importingSettings,
                                           @NotNull HashMap<MavenProject, String> mavenProjectToModuleName) {
    myProject = project;
    myProjectsTree = projectsTree;
    myImportingSettings = importingSettings;
    myMavenProjectToModuleName = mavenProjectToModuleName;
  }

  public MavenModuleImportContext getContext(@NotNull Map<MavenProject, MavenProjectChanges> projectsWithChanges) {
    ModuleImportDataContext importDataContext = getModuleImportDataContext(projectsWithChanges);
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

  private @NotNull ModuleImportDataContext getModuleImportDataContext(@NotNull Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges) {
    boolean hasChanges = false;
    List<MavenProjectImportData> allModules = new ArrayList<>();
    Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId = new TreeMap<>(Comparator.comparing(MavenId::getKey));

    Map<String, Module> legacyModuleByName = buildModuleByNameMap();

    for (var each : projectsToImportWithChanges.entrySet()) {
      MavenProject project = each.getKey();
      MavenProjectChanges changes = each.getValue();

      String moduleName = getModuleName(project);
      if (StringUtil.isEmpty(moduleName)) {
        MavenLog.LOG.warn("[import context] empty module name for project " + project);
        continue;
      }

      MavenProjectImportData mavenProjectImportData = getModuleImportData(project, moduleName, legacyModuleByName, changes);
      if (changes.hasChanges()) {
        hasChanges = true;
      }
      moduleImportDataByMavenId.put(project.getMavenId(), mavenProjectImportData);
      allModules.add(mavenProjectImportData);
    }

    return new ModuleImportDataContext(allModules, myMavenProjectToModuleName, moduleImportDataByMavenId,
                                       new ArrayList<>(legacyModuleByName.values()), hasChanges);
  }

  @Nullable
  protected String getModuleName(MavenProject project) {
    return myMavenProjectToModuleName.get(project);
  }

  protected Map<String, Module> buildModuleByNameMap() {
    return Collections.emptyMap();
  }

  @NotNull
  private MavenProjectImportContextProvider.ModuleImportDataDependecyContext getFlattenModuleDataDependencyContext(
    ModuleImportDataContext context) {
    List<Module> legacyCreatedModules = new ArrayList<>();
    List<MavenTreeModuleImportData> allModuleDataWithDependencies = new ArrayList<>();
    List<MavenTreeModuleImportData> changedModuleDataWithDependencies = new ArrayList<>();

    MavenModuleImportDependencyProvider dependencyProvider =
      new MavenModuleImportDependencyProvider(myProject, context.moduleImportDataByMavenId, myImportingSettings, myProjectsTree);

    for (MavenProjectImportData importData : context.importData) {
      MavenModuleImportDataWithDependencies importDataWithDependencies = dependencyProvider.getDependencies(importData);
      List<MavenTreeModuleImportData> mavenModuleImportDataList = splitToModules(importDataWithDependencies);
      for (MavenTreeModuleImportData moduleImportData : mavenModuleImportDataList) {
        if (moduleImportData.getChanges().hasChanges()) changedModuleDataWithDependencies.add(moduleImportData);

        addLegacyCreatedModule(legacyCreatedModules, moduleImportData);

        allModuleDataWithDependencies.add(moduleImportData);
      }
    }

    return new ModuleImportDataDependecyContext(allModuleDataWithDependencies, changedModuleDataWithDependencies, legacyCreatedModules);
  }

  protected void addLegacyCreatedModule(List<Module> createdModules, MavenTreeModuleImportData moduleImportData) {
  }

  @NotNull
  protected static List<MavenTreeModuleImportData> splitToModules(MavenModuleImportDataWithDependencies dataWithDependencies) {
    SplittedMainAndTestModules mainAndTestModules = dataWithDependencies.getModuleImportData().getSplittedMainAndTestModules();
    MavenProject project = dataWithDependencies.getModuleImportData().getMavenProject();
    ModuleData moduleData = dataWithDependencies.getModuleImportData().getModuleData();
    MavenProjectChanges changes = dataWithDependencies.getModuleImportData().getChanges();

    if (mainAndTestModules != null) {
      List<MavenTreeModuleImportData> result = new ArrayList<>(3);
      result.add(new MavenTreeModuleImportData(
        project, moduleData, Collections.emptyList(), dataWithDependencies.getModuleImportData().getChanges()
      ));
      result.add(new MavenTreeModuleImportData(
        project, mainAndTestModules.getMainData(), dataWithDependencies.getMainDependencies(), changes
      ));
      List<MavenImportDependency<?>> dependencies = concat(dataWithDependencies.getTestDependencies(),
                                                           dataWithDependencies.getMainDependencies());
      result.add(new MavenTreeModuleImportData(project, mainAndTestModules.getTestData(), dependencies, changes
      ));
      return result;
    }

    return List.of(new MavenTreeModuleImportData(
      project, moduleData, concat(dataWithDependencies.getMainDependencies(), dataWithDependencies.getTestDependencies()), changes
    ));
  }

  protected MavenProjectImportData getModuleImportData(MavenProject project,
                                                       String moduleName,
                                                       Map<String, Module> legacyModuleByName,
                                                       MavenProjectChanges changes) {
    MavenJavaVersionHolder javaVersions = getMavenJavaVersions(project);
    StandardMavenModuleType type = getModuleType(project, javaVersions);

    ModuleData moduleData = getModuleData(project, moduleName, type, javaVersions, legacyModuleByName);
    if (type != StandardMavenModuleType.COMPOUND_MODULE) {
      return new MavenProjectImportData(project, moduleData, changes, null);
    }
    String moduleMainName = moduleName + MAIN_SUFFIX;
    ModuleData mainData = getModuleData(project, moduleMainName, StandardMavenModuleType.MAIN_ONLY, javaVersions, legacyModuleByName);

    String moduleTestName = moduleName + TEST_SUFFIX;
    ModuleData testData = getModuleData(project, moduleTestName, StandardMavenModuleType.TEST_ONLY, javaVersions, legacyModuleByName);

    SplittedMainAndTestModules mainAndTestModules = new SplittedMainAndTestModules(mainData, testData);
    return new MavenProjectImportData(project, moduleData, changes, mainAndTestModules);
  }

  private static StandardMavenModuleType getModuleType(MavenProject project, MavenJavaVersionHolder mavenJavaVersions) {
    if (needSplitMainAndTest(project, mavenJavaVersions)) {
      return StandardMavenModuleType.COMPOUND_MODULE;
    }
    else if (project.isAggregator()) {
      return StandardMavenModuleType.AGGREGATOR;
    }
    else {
      return StandardMavenModuleType.SINGLE_MODULE;
    }
  }

  private static boolean needSplitMainAndTest(MavenProject project, MavenJavaVersionHolder mavenJavaVersions) {
    if (!Registry.is("maven.import.separate.main.and.test.modules.when.needed")) return false;
    return !project.isAggregator() && mavenJavaVersions.needSeparateTestModule() && isCompilerTestSupport(project);
  }

  protected ModuleData getModuleData(MavenProject project, String moduleName,
                                     StandardMavenModuleType type,
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
    @NotNull final List<MavenTreeModuleImportData> allModuleDataWithDependencies;
    @NotNull final List<MavenTreeModuleImportData> changedModuleDataWithDependencies;
    @NotNull final List<Module> legacyCreatedModules;

    private ModuleImportDataDependecyContext(@NotNull List<MavenTreeModuleImportData> allModuleDataWithDependencies,
                                             @NotNull List<MavenTreeModuleImportData> changedModuleDataWithDependencies,
                                             @NotNull List<Module> legacyCreatedModules) {
      this.allModuleDataWithDependencies = allModuleDataWithDependencies;
      this.changedModuleDataWithDependencies = changedModuleDataWithDependencies;
      this.legacyCreatedModules = legacyCreatedModules;
    }
  }
}


