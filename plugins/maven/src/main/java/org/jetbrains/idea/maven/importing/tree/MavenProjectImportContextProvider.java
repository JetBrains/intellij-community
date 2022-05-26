// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenModelUtil;
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper;
import org.jetbrains.idea.maven.importing.ModuleModelProxy;
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.concat;
import static org.jetbrains.idea.maven.importing.MavenModelUtil.isCompilerTestSupport;

public class MavenProjectImportContextProvider {
  public static final String TEST_SUFIX = ".test";
  public static final String MAIN_SUFIX = ".main";
  @NotNull
  private final Project myProject;
  @NotNull
  private final MavenProjectsTree myProjectsTree;
  @NotNull
  private final Map<MavenProject, MavenProjectChanges> myProjectsToImportWithChanges;
  @NotNull
  private final ModuleModelProxy myModuleModel;
  @NotNull
  private final MavenImportingSettings myImportingSettings;

  public MavenProjectImportContextProvider(@NotNull Project project,
                                           @NotNull MavenProjectsTree projectsTree,
                                           @NotNull Map<MavenProject, MavenProjectChanges> changes,
                                           @NotNull ModuleModelProxy moduleModel,
                                           @NotNull MavenImportingSettings importingSettings) {
    myProject = project;
    myProjectsTree = projectsTree;
    myProjectsToImportWithChanges = changes;
    myModuleModel = moduleModel;
    myImportingSettings = importingSettings;
  }

  public MavenModuleImportContext getContext() {
    ModuleImportDataContext importDataContext = getModuleImportDataContext();
    ModuleImportDataDependecyContext importDataDependencyContext = getFlattenModuleDataDependencyContext(importDataContext);

    return new MavenModuleImportContext(
      importDataDependencyContext.changedModuleDataWithDependencies,
      importDataDependencyContext.allModuleDataWithDependencies,
      importDataDependencyContext.createdModules,
      importDataContext.obsoleteModules,
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

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    Map<String, Module> moduleByName = Arrays.stream(myModuleModel.getModules())
      .filter(m -> projectsManager.isMavenizedModule(m))
      .collect(Collectors.toMap(m -> m.getName(), Function.identity()));

    for (MavenProject project : myProjectsTree.getProjects()) {
      if (myProjectsTree.isIgnored(project)) continue;

      String moduleName = getModuleName(project, myProjectsTree, moduleNameByProject);
      if (StringUtil.isEmpty(moduleName)) {
        MavenLog.LOG.warn("[import context] empty module name for project " + project);
        continue;
      }

      MavenProjectChanges changes = myProjectsToImportWithChanges.get(project);
      MavenProjectImportData mavenProjectImportData = getModuleImportData(project, moduleName, moduleByName, changes);

      if (changes != null && changes.hasChanges()) {
        hasChanges = true;
      }
      moduleImportDataByMavenId.put(project.getMavenId(), mavenProjectImportData);
      allModules.add(mavenProjectImportData);
    }

    return new ModuleImportDataContext(allModules, moduleNameByProject, moduleImportDataByMavenId,
                                       new ArrayList<>(moduleByName.values()), hasChanges);
  }

  @NotNull
  private MavenProjectImportContextProvider.ModuleImportDataDependecyContext getFlattenModuleDataDependencyContext(
    ModuleImportDataContext context) {
    List<Module> createdModules = new ArrayList<>();
    List<MavenModuleImportData> allModuleDataWithDependencies = new ArrayList<>();
    List<MavenModuleImportData> changedModuleDataWithDependencies = new ArrayList<>();

    MavenModuleImportDependencyProvider dependencyProvider =
      new MavenModuleImportDependencyProvider(myProject, context.moduleImportDataByMavenId, myImportingSettings);

    for (MavenProjectImportData importData : context.importData) {
      MavenModuleImportDataWithDependencies importDataWithDependencies = dependencyProvider.getDependencies(importData);
      List<MavenModuleImportData> mavenModuleImportDataList = splitToModules(importDataWithDependencies);
      for (MavenModuleImportData moduleImportData : mavenModuleImportDataList) {
        if (moduleImportData.hasChanges()) changedModuleDataWithDependencies.add(moduleImportData);
        if (moduleImportData.isNewModule()) createdModules.add(moduleImportData.getModuleData().getModule());
        allModuleDataWithDependencies.add(moduleImportData);
      }
    }

    return new ModuleImportDataDependecyContext(allModuleDataWithDependencies, changedModuleDataWithDependencies, createdModules);
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

  private void deleteExistingFiles(String moduleName, String modulePath) {
    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    deleteExistingImlFile(modulePath);
    deleteExistingModuleByName(moduleName);
  }

  @NotNull
  public static String getModuleName(@NotNull MavenProject mavenProject, @NotNull Project project) {
    MavenProjectsTree projectsTree = MavenProjectsManager.getInstance(project).getProjectsTree();
    return projectsTree != null ? getModuleName(mavenProject, projectsTree, new HashMap<>()) : StringUtils.EMPTY;
  }

  @NotNull
  private static String getModuleName(@NotNull MavenProject project,
                                      @NotNull MavenProjectsTree projectsTree,
                                      @NotNull Map<MavenProject, String> moduleNameMap) {
    String moduleName = moduleNameMap.get(project);
    if (moduleName != null) return moduleName;
    moduleName = project.getMavenId().getArtifactId();
    if (moduleName == null) return StringUtils.EMPTY;
    if (project.getParentId() != null) {
      MavenProject parentProject = projectsTree.findProject(project.getParentId());
      if (parentProject != null) {
        String parentName = getModuleName(parentProject, projectsTree, moduleNameMap);
        if (StringUtil.isNotEmpty(parentName)) {
          moduleName = parentName + "." + moduleName;
        }
      }
    }
    moduleNameMap.put(project, moduleName);
    return moduleName;
  }

  private MavenProjectImportData getModuleImportData(MavenProject project,
                                                     String moduleName,
                                                     Map<String, Module> moduleByName,
                                                     MavenProjectChanges changes) {
    MavenJavaVersionHolder javaVersions = MavenModelUtil.getMavenJavaVersions(project);
    MavenModuleType type = getModuleType(project, javaVersions);

    ModuleData moduleData = getModuleData(project, moduleName, type, javaVersions, moduleByName);
    if (type != MavenModuleType.AGGREGATOR_MAIN_TEST) {
      return new MavenProjectImportData(project, moduleData, changes, null);
    }
    String moduleMainName = moduleName + MAIN_SUFIX;
    ModuleData mainData = getModuleData(project, moduleMainName, MavenModuleType.MAIN, javaVersions, moduleByName);

    String moduleTestName = moduleName + TEST_SUFIX;
    ModuleData testData = getModuleData(project, moduleTestName, MavenModuleType.TEST, javaVersions, moduleByName);

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

  private ModuleData getModuleData(MavenProject project, String moduleName,
                                   MavenModuleType type,
                                   MavenJavaVersionHolder javaVersionHolder,
                                   Map<String, Module> moduleByName) {
    Module module = moduleByName.remove(moduleName);
    if (module != null && !(ModuleType.get(module).equals(project.getModuleType()))) {
      myModuleModel.disposeModule(module);
      module = null;
    }
    boolean newModule = module == null;
    if (newModule) {
      String modulePath = MavenModuleNameMapper
        .generateModulePath(getModuleDirPath(project, type), moduleName, myImportingSettings.getDedicatedModuleDir());
      deleteExistingFiles(moduleName, modulePath);
      module = myModuleModel.newModule(modulePath, project.getModuleType().getId());
    }
    return new ModuleData(module, type, javaVersionHolder, newModule);
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

  private void deleteExistingModuleByName(final String name) {
    Module module = myModuleModel.findModuleByName(name);
    if (module != null) {
      myModuleModel.disposeModule(module);
    }
  }

  private void deleteExistingImlFile(String path) {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          if (file != null) file.delete(this);
        }
        catch (IOException e) {
          MavenLog.LOG.warn("Cannot delete existing iml file: " + path, e);
        }
      }
    });
  }

  private static class ModuleImportDataContext {
    @NotNull final List<MavenProjectImportData> importData;
    @NotNull final Map<MavenProject, String> moduleNameByProject;
    @NotNull final Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId;
    @NotNull final List<Module> obsoleteModules;
    final boolean hasChanges;

    private ModuleImportDataContext(@NotNull List<MavenProjectImportData> importData,
                                    @NotNull Map<MavenProject, String> moduleNameByProject,
                                    @NotNull Map<MavenId, MavenProjectImportData> moduleImportDataByMavenId,
                                    @NotNull List<Module> obsoleteModules,
                                    boolean hasChanges) {
      this.importData = importData;
      this.moduleNameByProject = moduleNameByProject;
      this.moduleImportDataByMavenId = moduleImportDataByMavenId;
      this.obsoleteModules = obsoleteModules;
      this.hasChanges = hasChanges;
    }
  }

  private static class ModuleImportDataDependecyContext {
    @NotNull final List<MavenModuleImportData> allModuleDataWithDependencies;
    @NotNull final List<MavenModuleImportData> changedModuleDataWithDependencies;
    @NotNull final List<Module> createdModules;

    private ModuleImportDataDependecyContext(@NotNull List<MavenModuleImportData> allModuleDataWithDependencies,
                                             @NotNull List<MavenModuleImportData> changedModuleDataWithDependencies,
                                             @NotNull List<Module> createdModules) {
      this.allModuleDataWithDependencies = allModuleDataWithDependencies;
      this.changedModuleDataWithDependencies = changedModuleDataWithDependencies;
      this.createdModules = createdModules;
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
