// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.ParallelRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class MavenProjectsTreeUpdater {
  private final MavenProjectsTree tree;
  private final MavenExplicitProfiles explicitProfiles;
  private final MavenProjectsTree.UpdateContext updateContext;
  private final MavenProjectReader reader;
  private final MavenGeneralSettings generalSettings;
  private final ProgressIndicator process;
  private final ConcurrentHashMap<VirtualFile, Boolean> updated = new ConcurrentHashMap<>();
  private final boolean updateModules;
  private final @Nullable File userSettingsFile;
  private final @Nullable File globalSettingsFile;

  MavenProjectsTreeUpdater(MavenProjectsTree tree,
                           MavenExplicitProfiles profiles,
                           MavenProjectsTree.UpdateContext context,
                           MavenProjectReader reader,
                           MavenGeneralSettings settings,
                           ProgressIndicator process,
                           boolean updateModules) {
    this.tree = tree;
    explicitProfiles = profiles;
    updateContext = context;
    this.reader = reader;
    generalSettings = settings;
    this.process = process;
    this.updateModules = updateModules;
    userSettingsFile = generalSettings.getEffectiveUserSettingsIoFile();
    globalSettingsFile = generalSettings.getEffectiveGlobalSettingsIoFile();
  }

  private boolean startUpdate(VirtualFile mavenProjectFile, boolean forceRead) {
    String projectPath = mavenProjectFile.getPath();

    if (tree.isIgnored(projectPath)) return false;

    Ref<Boolean> previousUpdateRef = new Ref<>();
    updated.compute(mavenProjectFile, (file, value) -> {
      previousUpdateRef.set(value);
      return Boolean.TRUE.equals(value) || forceRead;
    });
    var previousUpdate = previousUpdateRef.get();

    if ((null != previousUpdate && !forceRead) || Boolean.TRUE.equals(previousUpdate)) {
      // we already updated this file
      MavenLog.LOG.trace("Has already been updated (%s): %s; forceRead: %s".formatted(previousUpdate, mavenProjectFile, forceRead));
      return false;
    }
    if (null != process) {
      process.setText(MavenProjectBundle.message("maven.reading.pom", projectPath));
      process.setText2("");
    }
    return true;
  }

  private boolean readPomIfNeeded(@NotNull MavenProject mavenProject, boolean forceRead) {
    var timestamp = calculateTimestamp(mavenProject);
    boolean timeStampChanged = !timestamp.equals(tree.getTimeStamp(mavenProject));
    boolean readPom = forceRead || timeStampChanged;

    if (readPom) {
      MavenId oldProjectId = mavenProject.isNew() ? null : mavenProject.getMavenId();
      MavenId oldParentId = mavenProject.getParentId();
      var readChanges = mavenProject.read(generalSettings, explicitProfiles, reader, tree.getProjectLocator());
      tree.putVirtualFileToProjectMapping(mavenProject, oldProjectId);

      if (Comparing.equal(oldParentId, mavenProject.getParentId())) {
        tree.putTimestamp(mavenProject, timestamp);
      }
      else {
        // ensure timestamp reflects actual parent's timestamp
        var newTimestamp = calculateTimestamp(mavenProject);
        tree.putTimestamp(mavenProject, newTimestamp);
      }

      var forcedChanges = forceRead ? MavenProjectChanges.ALL : MavenProjectChanges.NONE;
      var changes = MavenProjectChangesBuilder.merged(forcedChanges, readChanges);
      updateContext.updated(mavenProject, changes);
    }

    return readPom;
  }

  private MavenProjectsTree.MavenProjectTimestamp calculateTimestamp(MavenProject mavenProject) {
    return ReadAction.compute(() -> {
      long pomTimestamp = getFileTimestamp(mavenProject.getFile());
      MavenProject parent = tree.findParent(mavenProject);
      long parentLastReadStamp = parent == null ? -1 : parent.getLastReadStamp();
      VirtualFile profilesXmlFile = mavenProject.getProfilesXmlFile();
      long profilesTimestamp = getFileTimestamp(profilesXmlFile);
      VirtualFile jvmConfigFile = MavenUtil.getConfigFile(mavenProject, MavenConstants.JVM_CONFIG_RELATIVE_PATH);
      long jvmConfigTimestamp = getFileTimestamp(jvmConfigFile);
      VirtualFile mavenConfigFile = MavenUtil.getConfigFile(mavenProject, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
      long mavenConfigTimestamp = getFileTimestamp(mavenConfigFile);
      long userSettingsTimestamp = getFileTimestamp(userSettingsFile);
      long globalSettingsTimestamp = getFileTimestamp(globalSettingsFile);

      int profilesHashCode = explicitProfiles.hashCode();

      return new MavenProjectsTree.MavenProjectTimestamp(pomTimestamp,
                                                         parentLastReadStamp,
                                                         profilesTimestamp,
                                                         userSettingsTimestamp,
                                                         globalSettingsTimestamp,
                                                         profilesHashCode,
                                                         jvmConfigTimestamp,
                                                         mavenConfigTimestamp);
    });
  }

  private static long getFileTimestamp(@Nullable VirtualFile file) {
    if (file == null || !file.isValid()) return -1;
    return file.getTimeStamp();
  }

  private static long getFileTimestamp(@Nullable File file) {
    return getFileTimestamp(file == null ? null : LocalFileSystem.getInstance().findFileByIoFile(file));
  }

  private void handleRemovedModules(MavenProject mavenProject, List<MavenProject> prevModules, List<VirtualFile> existingModuleFiles) {
    var removedModules = ContainerUtil.filter(prevModules, prevModule -> !existingModuleFiles.contains(prevModule.getFile()));
    for (MavenProject module : removedModules) {
      VirtualFile moduleFile = module.getFile();
      if (tree.isManagedFile(moduleFile)) {
        if (tree.reconnectRoot(module)) {
          updateContext.updated(module, MavenProjectChanges.NONE);
        }
      }
      else {
        tree.removeModule(mavenProject, module);
        tree.doDelete(mavenProject, module, updateContext);
      }
    }
  }

  private void reconnectModuleFiles(MavenProject mavenProject, List<VirtualFile> modulesFilesToReconnect) {
    for (var file : modulesFilesToReconnect) {
      MavenProject module = tree.findProject(file);
      if (null != module) {
        if (tree.reconnect(mavenProject, module)) {
          updateContext.updated(module, MavenProjectChanges.NONE);
        }
      }
    }
  }

  private List<VirtualFile> collectModuleFilesToReconnect(MavenProject mavenProject, List<VirtualFile> existingModuleFiles) {
    var modulesFilesToReconnect = new ArrayList<VirtualFile>();
    for (VirtualFile moduleFile : existingModuleFiles) {
      MavenProject foundModule = tree.findProject(moduleFile);
      boolean isNewModule = foundModule == null;
      if (!isNewModule) {
        MavenProject currentAggregator = tree.findAggregator(foundModule);
        if (currentAggregator != null && currentAggregator != mavenProject) {
          MavenLog.LOG.info("Module " + moduleFile + " is already included into " + mavenProject.getFile());
          continue;
        }
      }

      modulesFilesToReconnect.add(moduleFile);
    }
    return modulesFilesToReconnect;
  }

  private List<VirtualFile> collectModuleFilesToUpdate(List<VirtualFile> moduleFilesToReconnect, boolean updateExistingModules) {
    if (updateExistingModules) {
      return moduleFilesToReconnect;
    }

    // update only new modules
    return ContainerUtil.filter(moduleFilesToReconnect, moduleFile -> null == tree.findProject(moduleFile));
  }

  private List<VirtualFile> collectChildFilesToUpdate(MavenProject mavenProject, Collection<MavenProject> prevChildren) {
    var children = new HashSet<>(prevChildren);
    children.removeAll(updateContext.getDeletedProjects());
    children.addAll(tree.findInheritors(mavenProject));
    return ContainerUtil.map(children, child -> child.getFile());
  }

  private MavenProject findOrCreateProject(VirtualFile f) {
    var mavenProject = tree.findProject(f);
    return null == mavenProject ? new MavenProject(f) : mavenProject;
  }

  private void update(final VirtualFile mavenProjectFile, final boolean forceRead) {
    // if the file has already been updated, skip subsequent updates
    if (!startUpdate(mavenProjectFile, forceRead)) return;

    var mavenProject = findOrCreateProject(mavenProjectFile);

    // we will compare modules and children before and after reading the pom.xml file
    var prevModules = tree.getModules(mavenProject);
    var prevChildren = tree.findInheritors(mavenProject);

    // read pom.xml if something has changed since the last reading or reading is forced
    boolean readPom = readPomIfNeeded(mavenProject, forceRead);

    var existingModuleFiles = mavenProject.getExistingModuleFiles();

    // some modules might have been removed
    handleRemovedModules(mavenProject, prevModules, existingModuleFiles);

    // collect new and existing modules to reconnect to the tree
    var modulesFilesToReconnect = collectModuleFilesToReconnect(mavenProject, existingModuleFiles);
    boolean updateExistingModules = readPom || updateModules;

    // collect modules to update recursively
    var modulesFilesToUpdate = collectModuleFilesToUpdate(modulesFilesToReconnect, updateExistingModules);

    // do not force update modules if only this project was requested to be updated
    var forceReadModules = updateModules && forceRead;
    var moduleUpdates = ContainerUtil.map(modulesFilesToUpdate, moduleFile ->
      new UpdateSpec(
        moduleFile,
        forceReadModules
      ));
    updateProjects(moduleUpdates);
    reconnectModuleFiles(mavenProject, modulesFilesToReconnect);

    // collect children (inheritors) to update recursively
    var childFilesToUpdate = collectChildFilesToUpdate(mavenProject, prevChildren);
    var childUpdates = ContainerUtil.map(childFilesToUpdate, childFile ->
      new UpdateSpec(
        childFile,
        readPom // if parent was read, force read children
      ));
    updateProjects(childUpdates);
  }

  public void updateProjects(@NotNull List<UpdateSpec> specs) {
    if (specs.isEmpty()) return;

    ParallelRunner.getInstance(tree.getProject()).runInParallelBlocking(specs, spec -> {
      update(spec.mavenProjectFile(), spec.forceRead());
    });
  }

  @ApiStatus.Internal
  record UpdateSpec(VirtualFile mavenProjectFile, boolean forceRead) {
  }
}
