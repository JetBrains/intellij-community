// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Internal
public final class MavenProjectManagerWatcher {

  private final Project myProject;
  private final MavenProjectAware myProjectAware;
  private final MavenProfileWatcher myProfileWatcher;
  private final MavenRenameModuleWatcher myRenameModuleWatcher;
  private final MavenGeneralSettingsWatcher myGeneralSettingsWatcher;
  private final Disposable myDisposable;
  private final AtomicBoolean myStarted = new AtomicBoolean(false);

  public MavenProjectManagerWatcher(Project project) {
    myProject = project;

    var backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenProjectsManagerWatcher.backgroundExecutor", 1);
    var projectManager = MavenProjectsManager.getInstance(myProject);
    var projectTracker = ExternalSystemProjectTracker.getInstance(myProject);
    var projectId = new ExternalSystemProjectId(MavenUtil.SYSTEM_ID, myProject.getName());
    myProjectAware = new MavenProjectAware(project, projectId, projectManager);
    myProfileWatcher = new MavenProfileWatcher(projectId, projectTracker, projectManager);
    myRenameModuleWatcher = new MavenRenameModuleWatcher();
    myGeneralSettingsWatcher = new MavenGeneralSettingsWatcher(projectManager, backgroundExecutor);
    myDisposable = Disposer.newDisposable(projectManager, MavenProjectManagerWatcher.class.toString());
  }

  public synchronized void start() {
    if (!myStarted.compareAndSet(false, true)) {
      MavenLog.LOG.error("Trying to start the watcher one more time", new Exception());
      return;
    }
    var busConnection = myProject.getMessageBus().connect(myDisposable);
    busConnection.subscribe(ModuleListener.TOPIC, myRenameModuleWatcher);
    myGeneralSettingsWatcher.subscribeOnSettingsChanges(myDisposable);
    myGeneralSettingsWatcher.subscribeOnSettingsFileChanges(myDisposable);
    var projectsManager = MavenProjectsManager.getInstance(myProject);
    var projectTracker = ExternalSystemProjectTracker.getInstance(myProject);
    projectTracker.register(myProjectAware, projectsManager);
    projectTracker.activate(myProjectAware.getProjectId());
    myProfileWatcher.subscribeOnProfileChanges(myDisposable);
  }

  @TestOnly
  public synchronized void enableAutoImportInTests() {
    AutoImportProjectTracker.Companion.enableAutoReloadInTests(myDisposable);
  }

  public synchronized void stop() {
    Disposer.dispose(myDisposable);
  }
}
