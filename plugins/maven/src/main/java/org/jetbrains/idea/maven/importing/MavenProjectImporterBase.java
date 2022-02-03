// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.configurers.MavenModuleConfigurer;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenArtifactUtilKt;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.*;

public abstract class MavenProjectImporterBase implements MavenProjectImporter {
  private static final Logger LOG = Logger.getInstance(MavenProjectImporterBase.class);
  protected final MavenProjectsTree myProjectsTree;
  protected final MavenImportingSettings myImportingSettings;
  protected volatile Map<MavenProject, MavenProjectChanges> myProjectsToImportWithChanges;

  public MavenProjectImporterBase(MavenProjectsTree projectsTree, MavenImportingSettings importingSettings,
                                  Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges) {
    myProjectsTree = projectsTree;
    myImportingSettings = importingSettings;
    myProjectsToImportWithChanges = projectsToImportWithChanges;
  }

  protected Set<MavenProject> selectProjectsToImport(Collection<MavenProject> originalProjects) {
    Set<MavenProject> result = new HashSet<>();
    for (MavenProject each : originalProjects) {
      if (!shouldCreateModuleFor(each)) continue;
      result.add(each);
    }
    return result;
  }

  protected boolean shouldCreateModuleFor(MavenProject project) {
    if (myProjectsTree.isIgnored(project)) return false;
    return !project.isAggregator() || myImportingSettings.isCreateModulesForAggregators();
  }

  protected void scheduleRefreshResolvedArtifacts(List<MavenProjectsProcessorTask> postTasks) {
    // We have to refresh all the resolved artifacts manually in order to
    // update all the VirtualFilePointers. It is not enough to call
    // VirtualFileManager.refresh() since the newly created files will be only
    // picked by FS when FileWatcher finishes its work. And in the case of import
    // it doesn't finish in time.
    // I couldn't manage to write a test for this since behaviour of VirtualFileManager
    // and FileWatcher differs from real-life execution.

    List<MavenArtifact> artifacts = new ArrayList<>();
    for (MavenProject each : myProjectsToImportWithChanges.keySet()) {
      artifacts.addAll(each.getDependencies());
    }

    final Set<File> files = new HashSet<>();
    for (MavenArtifact each : artifacts) {
      if (MavenArtifactUtilKt.resolved(each)) files.add(each.getFile());
    }

    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      doRefreshFiles(files);
    }
    else {
      postTasks.add(new RefreshingFilesTask(files));
    }
  }

  protected boolean projectsToImportHaveChanges() {
    for (MavenProjectChanges each : myProjectsToImportWithChanges.values()) {
      if (each.hasChanges()) return true;
    }
    return false;
  }

  protected static void configureMavenProjectsInBackground(@NotNull Collection<MavenProject> projects,
                                                           @NotNull Map<MavenProject, Module> mavenProjectToModule,
                                                           @NotNull Project project) {
    if (Registry.is("maven.new.import")) return;

    MavenUtil.runInBackground(project, MavenProjectBundle.message("command.name.configuring.projects"), false, indicator -> {
      configureMavenProjects(projects, mavenProjectToModule, project, indicator);
    });
  }

  @IntellijInternalApi
  @ApiStatus.Internal
  public static void configureMavenProjects(@NotNull Collection<MavenProject> projects,
                                            @NotNull Map<MavenProject, Module> mavenProjectToModule,
                                            @NotNull Project project,
                                            MavenProgressIndicator indicator) {
    List<MavenModuleConfigurer> configurers = MavenModuleConfigurer.getConfigurers();
    float count = 0;
    long startTime = System.currentTimeMillis();
    LOG.info("[maven import] applying " + configurers.size() + " configurers to " + projects.size() + " Maven projects");
    for (MavenProject mavenProject : projects) {
      Module module = mavenProjectToModule.get(mavenProject);
      if (module == null) {
        continue;
      }
      indicator.setFraction(count++ / projects.size());
      indicator.setText2(MavenProjectBundle.message("progress.details.configuring.module", module.getName()));
      for (MavenModuleConfigurer configurer : configurers) {
        configurer.configure(mavenProject, project, module);
      }
    }
    LOG.info("[maven import] configuring projects took " + (System.currentTimeMillis() - startTime) + "ms");
  }

  protected static void doRefreshFiles(Set<File> files) {
    LocalFileSystem.getInstance().refreshIoFiles(files);
  }

  protected static class RefreshingFilesTask implements MavenProjectsProcessorTask {
    private final Set<File> myFiles;

    protected RefreshingFilesTask(Set<File> files) {
      myFiles = files;
    }

    @Override
    public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator) {
      indicator.setText(MavenProjectBundle.message("progress.text.refreshing.files"));
      doRefreshFiles(myFiles);
    }
  }
}
