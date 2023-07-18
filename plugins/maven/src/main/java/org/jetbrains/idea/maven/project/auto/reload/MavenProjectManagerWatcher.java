// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.concurrent.ExecutorService;

@ApiStatus.Internal
public final class MavenProjectManagerWatcher {

  private final Project myProject;
  private @NotNull MavenProjectsTree myProjectTree;

  private final MavenProjectAware myProjectAware;
  private final MavenRenameModuleWatcher myRenameModuleWatcher;
  private final MavenProjectRootWatcher myProjectRootWatcher;
  private final ExecutorService myBackgroundExecutor;
  private final Disposable myDisposable;

  public MavenProjectManagerWatcher(Project project, @NotNull MavenProjectsTree projectTree) {
    myBackgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenProjectsManagerWatcher.backgroundExecutor", 1);
    myProject = project;
    myProjectTree = projectTree;

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    myProjectAware = new MavenProjectAware(project, projectsManager, myBackgroundExecutor);
    myRenameModuleWatcher = new MavenRenameModuleWatcher();
    myProjectRootWatcher = new MavenProjectRootWatcher(projectsManager, this);
    myDisposable = Disposer.newDisposable(projectsManager, MavenProjectManagerWatcher.class.toString());
  }

  public synchronized void start() {
    MessageBusConnection busConnection = myProject.getMessageBus().connect(myDisposable);
    busConnection.subscribe(ProjectTopics.MODULES, myRenameModuleWatcher);
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, myProjectRootWatcher);
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

  public @NotNull MavenProjectsTree getProjectTree() {
    return myProjectTree;
  }

  public void setProjectTree(@NotNull MavenProjectsTree projectTree) {
    myProjectTree = projectTree;
  }
}
