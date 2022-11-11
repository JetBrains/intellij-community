// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenImportUtil;
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper;
import org.jetbrains.idea.maven.importing.ModuleModelProxy;
import org.jetbrains.idea.maven.importing.StandardMavenModuleType;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LegacyMavenProjectImportContextProvider extends MavenProjectImportContextProvider {
  @NotNull
  private final ModuleModelProxy myModuleModel;

  public LegacyMavenProjectImportContextProvider(@NotNull Project project,
                                                 @NotNull MavenProjectsTree projectsTree,
                                                 @NotNull ModuleModelProxy moduleModel,
                                                 @NotNull MavenImportingSettings importingSettings) {
    super(project, projectsTree, importingSettings, new HashMap<>());
    myModuleModel = moduleModel;
  }

  @Override
  protected @Nullable String getModuleName(MavenProject project) {
    return MavenImportUtil.getModuleName(project, myProjectsTree, myMavenProjectToModuleName);
  }

  @NotNull
  @Override
  protected Map<String, Module> buildModuleByNameMap() {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    return Arrays.stream(myModuleModel.getModules())
      .filter(m -> projectsManager.isMavenizedModule(m))
      .collect(Collectors.toMap(m -> m.getName(), Function.identity()));
  }

  @Override
  protected void addLegacyCreatedModule(List<Module> createdModules, MavenTreeModuleImportData moduleImportData) {
    if (moduleImportData.getLegacyModuleData().isNewModule()) createdModules.add(moduleImportData.getLegacyModuleData().getModule());
  }

  @Override
  protected ModuleData getModuleData(MavenProject project, String moduleName,
                                     StandardMavenModuleType type,
                                     MavenJavaVersionHolder javaVersionHolder,
                                     Map<String, Module> legacyModuleByName) {
    Module module = legacyModuleByName.remove(moduleName);
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
    return new LegacyModuleData(module, type, javaVersionHolder, newModule);
  }

  private static String getModuleDirPath(MavenProject project, StandardMavenModuleType type) {
    if (type == StandardMavenModuleType.TEST_ONLY) {
      return Path.of(project.getDirectory(), "src", "test").toString();
    }
    if (type == StandardMavenModuleType.MAIN_ONLY) {
      return Path.of(project.getDirectory(), "src", "main").toString();
    }
    return project.getDirectory();
  }

  private void deleteExistingFiles(String moduleName, String modulePath) {
    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    deleteExistingImlFile(modulePath);
    deleteExistingModuleByName(moduleName);
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
}

