// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.jetbrains.idea.maven.project.MavenGeneralSettingsWatcher.registerGeneralSettingsWatcher;

public class MavenProjectsManagerWatcher {

  private static final Logger LOG = Logger.getInstance(MavenProjectsManagerWatcher.class);

  private final Project myProject;
  private final MavenProjectsTree myProjectsTree;
  private final MavenGeneralSettings myGeneralSettings;
  private final MavenProjectsProcessor myReadingProcessor;
  private final MavenProjectsAware myProjectsAware;
  private final ExecutorService myBackgroundExecutor;
  private final Disposable myDisposable;

  public MavenProjectsManagerWatcher(Project project,
                                     MavenProjectsTree projectsTree,
                                     MavenGeneralSettings generalSettings,
                                     MavenProjectsProcessor readingProcessor) {
    myBackgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenProjectsManagerWatcher.backgroundExecutor", 1);
    myProject = project;
    myProjectsTree = projectsTree;
    myGeneralSettings = generalSettings;
    myReadingProcessor = readingProcessor;
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    myProjectsAware = new MavenProjectsAware(project, projectsTree, projectsManager, this, myBackgroundExecutor);
    myDisposable = Disposer.newDisposable(projectsManager, MavenProjectsManagerWatcher.class.toString());
  }

  public synchronized void start() {
    MessageBusConnection busConnection = myProject.getMessageBus().connect(myDisposable);
    busConnection.subscribe(ProjectTopics.MODULES, new MavenIgnoredModulesWatcher());
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootChangesListener());
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    registerGeneralSettingsWatcher(projectsManager, this, myBackgroundExecutor, myDisposable);
    ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(myProject);
    projectTracker.register(myProjectsAware, projectsManager);
    projectTracker.activate(myProjectsAware.getProjectId());
  }

  @TestOnly
  public synchronized void enableAutoImportInTests() {
    AutoImportProjectTracker.getInstance(myProject).enableAutoImportInTests();
  }

  public synchronized void stop() {
    Disposer.dispose(myDisposable);
  }

  public synchronized void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myProjectsTree.addManagedFilesWithProfiles(files, explicitProfiles);
    scheduleUpdateAll(false, true);
  }

  @TestOnly
  public synchronized void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myProjectsTree.resetManagedFilesAndProfiles(files, explicitProfiles);
    scheduleUpdateAll(false, true);
  }

  public synchronized void removeManagedFiles(List<VirtualFile> files) {
    myProjectsTree.removeManagedFiles(files);
    scheduleUpdateAll(false, true);
  }

  public synchronized void setExplicitProfiles(MavenExplicitProfiles profiles) {
    myProjectsTree.setExplicitProfiles(profiles);
    scheduleUpdateAll(false, false);
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   */
  public Promise<Void> scheduleUpdateAll(boolean force, final boolean forceImportAndResolve) {
    if(Registry.is("maven.new.import")) {
      return Promises.resolvedPromise();
    }

    final AsyncPromise<Void> promise = new AsyncPromise<>();
    // display all import activities using the same build progress
    MavenSyncConsole.startTransaction(myProject);
    try {
      Runnable onCompletion = createScheduleImportAction(forceImportAndResolve, promise);
      scheduleReadingTask(new MavenProjectsProcessorReadingTask(force, myProjectsTree, myGeneralSettings, onCompletion));
    }
    finally {
      promise.onProcessed(unused -> MavenSyncConsole.finishTransaction(myProject));
      promise.onProcessed(unused -> MavenSyncConsole.finishTransaction(myProject));
    }
    return promise;
  }

  public Promise<Void> scheduleUpdate(List<VirtualFile> filesToUpdate,
                                      List<VirtualFile> filesToDelete,
                                      boolean force,
                                      final boolean forceImportAndResolve) {

    if (Registry.is("maven.new.import")) {
      return Promises.resolvedPromise();
    }
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    // display all import activities using the same build progress
    MavenSyncConsole.startTransaction(myProject);
    try {
      Runnable onCompletion = createScheduleImportAction(forceImportAndResolve, promise);
      if (LOG.isDebugEnabled()) {
        String withForceOptionMessage = force ? " with force option" : "";
        LOG.debug("Scheduling update for " + myProjectsTree + withForceOptionMessage +
                  ". Files to update: " + filesToUpdate + ". Files to delete: " + filesToDelete);
      }

      scheduleReadingTask(new MavenProjectsProcessorReadingTask(
        filesToUpdate, filesToDelete, force, myProjectsTree, myGeneralSettings, onCompletion));
    }
    finally {
      promise.onProcessed(unused -> MavenSyncConsole.finishTransaction(myProject));
    }
    return promise;
  }

  /**
   * All changed documents must be saved before reading
   */
  private void scheduleReadingTask(@NotNull MavenProjectsProcessorReadingTask readingTask) {
    myReadingProcessor.scheduleTask(readingTask);
  }

  @NotNull
  private Runnable createScheduleImportAction(final boolean forceImportAndResolve, final AsyncPromise<Void> promise) {
    return () -> {
      if (myProject.isDisposed()) {
        promise.setError("Project disposed");
        return;
      }

      if (forceImportAndResolve) {
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
        projectsManager.scheduleImportAndResolve()
          .onSuccess(modules -> promise.setResult(null))
          .onError(t -> promise.setError(t));
      }
      else {
        promise.setResult(null);
      }
    };
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
        scheduleUpdate(newFiles, deletedFiles, false, false);
      }
    }
  }

  private class MavenIgnoredModulesWatcher implements ModuleListener {
    @Override
    public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
      if (Registry.is("maven.modules.do.not.ignore.on.delete")) return;
      if (MavenProjectImporter.isImportToTreeStructureEnabled()) return;

      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
      MavenProject mavenProject = projectsManager.findProject(module);
      if (mavenProject != null && !projectsManager.isIgnored(mavenProject)) {
        VirtualFile file = mavenProject.getFile();

        if (projectsManager.isManagedFile(file) && projectsManager.getModules(mavenProject).isEmpty()) {
          MavenLog.LOG.info("remove managed maven project  + " + mavenProject + "because there is no module for it");
          projectsManager.removeManagedFiles(Collections.singletonList(file));
        }
        else {
          if (projectsManager.getRootProjects().contains(mavenProject)) {
            MavenLog.LOG.info("Requested to ignore " + mavenProject + ", will not do it because it is a root project");
            return;
          }
          MavenLog.LOG.info("Ignoring " + mavenProject);
          projectsManager.setIgnoredState(Collections.singletonList(mavenProject), true);
        }
      }
    }

    @Override
    public void moduleAdded(@NotNull final Project project, @NotNull final Module module) {
      if (Registry.is("maven.modules.do.not.ignore.on.delete")) return;
      // this method is needed to return non-ignored status for modules that were deleted (and thus ignored) and then created again with a different module type

      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
      MavenProject mavenProject = projectsManager.findProject(module);
      if (mavenProject != null) projectsManager.setIgnoredState(Collections.singletonList(mavenProject), false);
    }
  }
}
