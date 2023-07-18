// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.buildtool.MavenImportSpec;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@ApiStatus.Internal
public final class MavenProjectManagerWatcher {

  private final Project myProject;
  private MavenProjectsTree myProjectsTree;
  private final MavenProjectAware myProjectAware;
  private final MavenRenameModuleWatcher myRenameModuleWatcher;
  private final ExecutorService myBackgroundExecutor;
  private final Disposable myDisposable;

  public MavenProjectManagerWatcher(Project project, MavenProjectsTree projectsTree) {
    myBackgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenProjectsManagerWatcher.backgroundExecutor", 1);
    myProject = project;
    myProjectsTree = projectsTree;
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    myProjectAware = new MavenProjectAware(project, projectsManager, myBackgroundExecutor);
    myRenameModuleWatcher = new MavenRenameModuleWatcher();
    myDisposable = Disposer.newDisposable(projectsManager, MavenProjectManagerWatcher.class.toString());
  }

  public synchronized void start() {
    MessageBusConnection busConnection = myProject.getMessageBus().connect(myDisposable);
    busConnection.subscribe(ProjectTopics.MODULES, myRenameModuleWatcher);
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootChangesListener());
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    MavenGeneralSettingsWatcher.registerGeneralSettingsWatcher(projectsManager, myBackgroundExecutor, myDisposable);
    ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(myProject);
    projectTracker.register(myProjectAware, projectsManager);
    projectTracker.activate(myProjectAware.getProjectId());
  }

  @TestOnly
  public synchronized void enableAutoImportInTests() {
    AutoImportProjectTracker.enableAutoReloadInTests(myDisposable);
  }

  public synchronized void stop() {
    Disposer.dispose(myDisposable);
  }

  public void setProjectsTree(MavenProjectsTree tree) {
    myProjectsTree = tree;
  }

  private class MyRootChangesListener implements ModuleRootListener {
    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
      // todo is this logic necessary?
      List<VirtualFile> existingFiles = myProjectsTree.getProjectsFiles();
      List<VirtualFile> newFiles = new ArrayList<>();
      List<VirtualFile> deletedFiles = new ArrayList<>();

      for (VirtualFile f : myProjectsTree.getExistingManagedFiles()) {
        if (!existingFiles.contains(f)) {
          newFiles.add(f);
        }
      }

      for (VirtualFile f : existingFiles) {
        if (!f.isValid()) deletedFiles.add(f);
      }

      if (!deletedFiles.isEmpty() || !newFiles.isEmpty()) {
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
        projectsManager.scheduleUpdate(newFiles, deletedFiles, new MavenImportSpec(false, false, true));
      }
    }
  }
}
