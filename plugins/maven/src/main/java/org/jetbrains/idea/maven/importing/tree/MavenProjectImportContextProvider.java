// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.project.Project;
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
  protected final Map<MavenProject, String> myMavenProjectToModuleName;

  public MavenProjectImportContextProvider(@NotNull Project project,
                                           @NotNull MavenProjectsTree projectsTree,
                                           @NotNull MavenImportingSettings importingSettings,
                                           @NotNull Map<MavenProject, String> mavenProjectToModuleName) {
    myProject = project;
    myProjectsTree = projectsTree;
    myImportingSettings = importingSettings;
    myMavenProjectToModuleName = mavenProjectToModuleName;
  }

  public MavenModuleImportContext getContext(@NotNull Map<MavenProject, MavenProjectChanges> projectsWithChanges) {
    ModuleImportDataContext importDataContext = getModuleImportDataContext(projectsWithChanges);
    ModuleImportDataDependencyContext importDataDependencyContext = getFlattenModuleDataDependencyContext(importDataContext);

    return new MavenModuleImportContext(
      importDataDependencyContext.changedModuleDataWithDependencies,
      importDataDependencyContext.allModuleDataWithDependencies,
      importDataContext.moduleNameByProject,
      importDataContext.hasChanges
    );
  }

  private @NotNull ModuleImportDataContext getModuleImportDataContext(@NotNull Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges) {
    boolean hasChanges = false;
    List<MavenProjectImportData> allModules = new ArrayList<>();
    Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId = new TreeMap<>(Comparator.comparing(MavenId::getKey));

    for (var each : projectsToImportWithChanges.entrySet()) {
      MavenProject project = each.getKey();
      MavenProjectChanges changes = each.getValue();

      String moduleName = getModuleName(project);
      if (StringUtil.isEmpty(moduleName)) {
        MavenLog.LOG.warn("[import context] empty module name for project " + project);
        continue;
      }

      MavenProjectImportData mavenProjectImportData = getModuleImportData(project, moduleName, changes);
      if (changes.hasChanges()) {
        hasChanges = true;
      }
      moduleImportDataByMavenId.put(project.getMavenId(), mavenProjectImportData);
      allModules.add(mavenProjectImportData);
    }

    return new ModuleImportDataContext(allModules, myMavenProjectToModuleName, moduleImportDataByMavenId, hasChanges);
  }

  @Nullable
  protected String getModuleName(MavenProject project) {
    return myMavenProjectToModuleName.get(project);
  }

  @NotNull
  private MavenProjectImportContextProvider.ModuleImportDataDependencyContext getFlattenModuleDataDependencyContext(
    ModuleImportDataContext context) {
    List<MavenTreeModuleImportData> allModuleDataWithDependencies = new ArrayList<>();
    List<MavenTreeModuleImportData> changedModuleDataWithDependencies = new ArrayList<>();

    MavenModuleImportDependencyProvider dependencyProvider =
      new MavenModuleImportDependencyProvider(context.moduleImportDataByMavenId, myImportingSettings, myProjectsTree);

    for (MavenProjectImportData importData : context.importData) {
      MavenModuleImportDataWithDependencies importDataWithDependencies = dependencyProvider.getDependencies(importData);
      List<MavenTreeModuleImportData> mavenModuleImportDataList = splitToModules(importDataWithDependencies);
      for (MavenTreeModuleImportData moduleImportData : mavenModuleImportDataList) {
        if (moduleImportData.getChanges().hasChanges()) changedModuleDataWithDependencies.add(moduleImportData);

        allModuleDataWithDependencies.add(moduleImportData);
      }
    }

    return new ModuleImportDataDependencyContext(allModuleDataWithDependencies, changedModuleDataWithDependencies);
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
                                                       MavenProjectChanges changes) {
    MavenJavaVersionHolder setUpJavaVersions = getMavenJavaVersions(project);
    MavenJavaVersionHolder javaVersions = adjustJavaVersions(setUpJavaVersions);

    StandardMavenModuleType type = getModuleType(project, javaVersions);

    ModuleData moduleData = new ModuleData(moduleName, type, javaVersions);
    if (type != StandardMavenModuleType.COMPOUND_MODULE) {
      return new MavenProjectImportData(project, moduleData, changes, null);
    }
    String moduleMainName = moduleName + MAIN_SUFFIX;
    ModuleData mainData = new ModuleData(moduleMainName, StandardMavenModuleType.MAIN_ONLY, javaVersions);

    String moduleTestName = moduleName + TEST_SUFFIX;
    ModuleData testData = new ModuleData(moduleTestName, StandardMavenModuleType.TEST_ONLY, javaVersions);

    SplittedMainAndTestModules mainAndTestModules = new SplittedMainAndTestModules(mainData, testData);
    return new MavenProjectImportData(project, moduleData, changes, mainAndTestModules);
  }

  private MavenJavaVersionHolder adjustJavaVersions(MavenJavaVersionHolder holder) {
    return new MavenJavaVersionHolder(
      holder.sourceLevel == null ? null : adjustLevelAndNotify(myProject, holder.sourceLevel),
      holder.targetLevel == null ? null : adjustLevelAndNotify(myProject, holder.targetLevel),
      holder.testSourceLevel == null ? null : adjustLevelAndNotify(myProject, holder.testSourceLevel),
      holder.testTargetLevel == null ? null : adjustLevelAndNotify(myProject, holder.testTargetLevel)
    );
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

  private static class ModuleImportDataDependencyContext {
    @NotNull final List<MavenTreeModuleImportData> allModuleDataWithDependencies;
    @NotNull final List<MavenTreeModuleImportData> changedModuleDataWithDependencies;

    private ModuleImportDataDependencyContext(@NotNull List<MavenTreeModuleImportData> allModuleDataWithDependencies,
                                              @NotNull List<MavenTreeModuleImportData> changedModuleDataWithDependencies) {
      this.allModuleDataWithDependencies = allModuleDataWithDependencies;
      this.changedModuleDataWithDependencies = changedModuleDataWithDependencies;
    }
  }
}


