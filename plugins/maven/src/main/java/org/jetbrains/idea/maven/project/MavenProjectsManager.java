/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import com.intellij.CommonBundle;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenProjectsManager", storages = {@Storage(file = StoragePathMacros.PROJECT_FILE)})
public class MavenProjectsManager extends MavenSimpleProjectComponent
  implements PersistentStateComponent<MavenProjectsManagerState>, SettingsSavingComponent {
  private static final int IMPORT_DELAY = 1000;
  private static final String NON_MANAGED_POM_NOTIFICATION_GROUP_ID = "Maven: non-managed pom.xml";
  private static final NotificationGroup NON_MANAGED_POM_NOTIFICATION_GROUP =
    NotificationGroup.balloonGroup(NON_MANAGED_POM_NOTIFICATION_GROUP_ID);

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

  private final Alarm myInitializationAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, myProject);

  private final MavenEmbeddersManager myEmbeddersManager;

  private MavenProjectsTree myProjectsTree;
  private MavenProjectsManagerWatcher myWatcher;

  private MavenProjectsProcessor myReadingProcessor;
  private MavenProjectsProcessor myResolvingProcessor;
  private MavenProjectsProcessor myPluginsResolvingProcessor;
  private MavenProjectsProcessor myFoldersResolvingProcessor;
  private MavenProjectsProcessor myArtifactsDownloadingProcessor;
  private MavenProjectsProcessor myPostProcessor;

  private MavenMergingUpdateQueue myImportingQueue;
  private final Object myImportingDataLock = new Object();
  private final Map<MavenProject, MavenProjectChanges> myProjectsToImport = new LinkedHashMap<MavenProject, MavenProjectChanges>();
  private final Set<MavenProject> myProjectsToResolve = new LinkedHashSet<MavenProject>();

  private boolean myImportModuleGroupsRequired = false;

  private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher =
    EventDispatcher.create(MavenProjectsTree.Listener.class);
  private final List<Listener> myManagerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private ModificationTracker myModificationTracker;

  private MavenWorkspaceSettings myWorkspaceSettings;

  public static MavenProjectsManager getInstance(Project p) {
    return p.getComponent(MavenProjectsManager.class);
  }

  public MavenProjectsManager(Project project) {
    super(project);
    myEmbeddersManager = new MavenEmbeddersManager(myProject);
    myModificationTracker = new MavenModificationTracker(this);
  }

  public MavenProjectsManagerState getState() {
    if (isInitialized()) {
      applyTreeToState();
    }
    return myState;
  }

  public void loadState(MavenProjectsManagerState state) {
    myState = state;
    if (isInitialized()) {
      applyStateToTree();
      scheduleUpdateAllProjects(false);
    }
  }

  public ModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  public MavenGeneralSettings getGeneralSettings() {
    return getWorkspaceSettings().generalSettings;
  }

  public MavenImportingSettings getImportingSettings() {
    return getWorkspaceSettings().importingSettings;
  }

  private MavenWorkspaceSettings getWorkspaceSettings() {
    if (myWorkspaceSettings == null) {
      myWorkspaceSettings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
    }

    return myWorkspaceSettings;
  }

  public File getLocalRepository() {
    return getGeneralSettings().getEffectiveLocalRepository();
  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;

    StartupManagerEx startupManager = StartupManagerEx.getInstanceEx(myProject);

    startupManager.registerStartupActivity(new Runnable() {
      public void run() {
        boolean wasMavenized = !myState.originalFiles.isEmpty();
        if (!wasMavenized) return;
        initMavenized();
      }
    });

    startupManager.registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        if (!isMavenizedProject()) {
          showNotificationOrphanMavenProject(myProject);
        }
        CompilerManager.getInstance(myProject).addBeforeTask(new CompileTask() {
          @Override
          public boolean execute(CompileContext context) {
            AccessToken token = ReadAction.start();

            try {
              new MavenResourceCompilerConfigurationGenerator(myProject, myProjectsTree).generateBuildConfiguration(context.isRebuild());
            }
            finally {
              token.finish();
            }
            return true;
          }
        });
      }
    });
  }

  private void showNotificationOrphanMavenProject(final Project project) {
    final NotificationSettings notificationSettings = NotificationsConfigurationImpl.getSettings(NON_MANAGED_POM_NOTIFICATION_GROUP_ID);
    if (!notificationSettings.isShouldLog() && notificationSettings.getDisplayType().equals(NotificationDisplayType.NONE)) {
      return;
    }

    File baseDir = VfsUtilCore.virtualToIoFile(project.getBaseDir());
    File pomXml = new File(baseDir, "pom.xml");

    if (pomXml.exists()) {
      final VirtualFile file = VfsUtil.findFileByIoFile(pomXml, true);
      if (file == null) return;

      showBalloon(
        ProjectBundle.message("maven.orphan.notification.title"),
        ProjectBundle.message("maven.orphan.notification.msg", file.getPresentableUrl()),
        NON_MANAGED_POM_NOTIFICATION_GROUP, NotificationType.INFORMATION, new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
            if ("#add".equals(e.getDescription())) {
              addManagedFilesOrUnignore(ContainerUtil.list(file));
              notification.expire();
            }
            else if ("#disable".equals(e.getDescription())) {
              final int result = Messages.showYesNoDialog(
                myProject,
                "Notification will be disabled for all projects.\n\n" +
                "Settings | Appearance & Behavior | Notifications | " +
                NON_MANAGED_POM_NOTIFICATION_GROUP_ID +
                "\ncan be used to configure the notification.",
                "Non-Managed Maven Project Detection",
                "Disable Notification", CommonBundle.getCancelButtonText(), Messages.getWarningIcon());
              if (result == Messages.YES) {
                NotificationsConfigurationImpl.getInstanceImpl().changeSettings(
                  NON_MANAGED_POM_NOTIFICATION_GROUP_ID, NotificationDisplayType.NONE, false, false);
                notification.expire();
              }
              else {
                notification.hideBalloon();
              }
            }
          }
        }
      );
    }
  }

  public void showBalloon(@NotNull final String title,
                          @NotNull final String message,
                          @NotNull final NotificationGroup group,
                          @NotNull final NotificationType type,
                          @Nullable final NotificationListener listener) {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      @Override
      public void run() {
        group.createNotification(title, message, type, listener).notify(myProject);
      }
    });
  }

  private void initMavenized() {
    doInit(false);
  }

  private void initNew(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myState.originalFiles = MavenUtil.collectPaths(files);
    getWorkspaceSettings().setEnabledProfiles(explicitProfiles.getEnabledProfiles());
    getWorkspaceSettings().setDisabledProfiles(explicitProfiles.getDisabledProfiles());
    doInit(true);
  }

  @TestOnly
  public void initForTests() {
    doInit(false);
  }

  private void doInit(final boolean isNew) {
    synchronized (isInitialized) {
      if (isInitialized.getAndSet(true)) return;

      initProjectsTree(!isNew);

      initWorkers();
      listenForSettingsChanges();
      listenForProjectsTreeChanges();

      MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
        public void run() {
          if (!isUnitTestMode()) {
            fireActivated();
            listenForExternalChanges();
          }
          scheduleUpdateAllProjects(isNew);
        }
      });
    }
  }

  private void initProjectsTree(boolean tryToLoadExisting) {
    if (tryToLoadExisting) {
      File file = getProjectsTreeFile();
      try {
        if (file.exists()) {
          myProjectsTree = MavenProjectsTree.read(file);
        }
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }

    if (myProjectsTree == null) myProjectsTree = new MavenProjectsTree();
    applyStateToTree();
    myProjectsTree.addListener(myProjectsTreeDispatcher.getMulticaster());
  }

  private void applyTreeToState() {
    myState.originalFiles = myProjectsTree.getManagedFilesPaths();
    myState.ignoredFiles = new THashSet<String>(myProjectsTree.getIgnoredFilesPaths());
    myState.ignoredPathMasks = myProjectsTree.getIgnoredFilesPatterns();
  }

  private void applyStateToTree() {
    MavenWorkspaceSettings settings = getWorkspaceSettings();
    MavenExplicitProfiles explicitProfiles = new MavenExplicitProfiles(settings.enabledProfiles, settings.disabledProfiles);
    myProjectsTree.resetManagedFilesPathsAndProfiles(myState.originalFiles, explicitProfiles);
    myProjectsTree.setIgnoredFilesPaths(new ArrayList<String>(myState.ignoredFiles));
    myProjectsTree.setIgnoredFilesPatterns(myState.ignoredPathMasks);
  }

  public void save() {
    if (myProjectsTree != null) {
      try {
        myProjectsTree.save(getProjectsTreeFile());
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }
  }

  private File getProjectsTreeFile() {
    return new File(getProjectsTreesDir(), myProject.getLocationHash() + "/tree.dat");
  }

  private static File getProjectsTreesDir() {
    return MavenUtil.getPluginSystemDir("Projects");
  }

  private void initWorkers() {
    myReadingProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.reading"), false, myEmbeddersManager);
    myResolvingProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.resolving"), true, myEmbeddersManager);
    myPluginsResolvingProcessor =
      new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.downloading.plugins"), true, myEmbeddersManager);
    myFoldersResolvingProcessor =
      new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.updating.folders"), true, myEmbeddersManager);
    myArtifactsDownloadingProcessor =
      new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.downloading"), true, myEmbeddersManager);
    myPostProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.post.processing"), true, myEmbeddersManager);

    myWatcher =
      new MavenProjectsManagerWatcher(myProject, this, myProjectsTree, getGeneralSettings(), myReadingProcessor, myEmbeddersManager);

    myImportingQueue = new MavenMergingUpdateQueue(getComponentName() + ": Importing queue", IMPORT_DELAY, !isUnitTestMode(), myProject);
    myImportingQueue.setPassThrough(false);

    myImportingQueue.makeUserAware(myProject);
    myImportingQueue.makeDumbAware(myProject);
    myImportingQueue.makeModalAware(myProject);
  }

  private void listenForSettingsChanges() {
    getImportingSettings().addListener(new MavenImportingSettings.Listener() {
      public void autoImportChanged() {
        if (myProject.isDisposed()) return;

        if (getImportingSettings().isImportAutomatically()) {
          scheduleImportAndResolve();
        }
      }

      public void createModuleGroupsChanged() {
        scheduleImportSettings(true);
      }

      public void createModuleForAggregatorsChanged() {
        scheduleImportSettings();
      }
    });
  }

  private void listenForProjectsTreeChanges() {
    myProjectsTree.addListener(new MavenProjectsTree.ListenerAdapter() {
      @Override
      public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
        if (!fromImport) scheduleImport();
      }

      @Override
      public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
        myEmbeddersManager.clearCaches();

        unscheduleAllTasks(deleted);

        List<MavenProject> updatedProjects = MavenUtil.collectFirsts(updated);

        // import only updated projects and dependents of them (we need to update faced-deps, packaging etc);
        List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<Pair<MavenProject, MavenProjectChanges>>(updated);

        for (MavenProject eachDependent : myProjectsTree.getDependentProjects(updatedProjects)) {
          toImport.add(Pair.create(eachDependent, MavenProjectChanges.DEPENDENCIES));
        }

        // resolve updated, theirs dependents, and dependents of deleted
        Set<MavenProject> toResolve = new THashSet<MavenProject>(updatedProjects);
        toResolve.addAll(myProjectsTree.getDependentProjects(ContainerUtil.concat(updatedProjects, deleted)));

        // do not try to resolve projects with syntactic errors
        Iterator<MavenProject> it = toResolve.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          if (each.hasReadingProblems()) it.remove();
        }

        if (haveChanges(toImport) || !deleted.isEmpty()) {
          scheduleForNextImport(toImport);
        }

        if (!deleted.isEmpty() && !hasScheduledProjects()) {
          MavenProject project = ObjectUtils.chooseNotNull(ContainerUtil.getFirstItem(toResolve),
                                                           ContainerUtil.getFirstItem(getNonIgnoredProjects()));
          if (project != null) {
            scheduleForNextImport(Pair.create(project, MavenProjectChanges.ALL));
            scheduleForNextResolve(ContainerUtil.list(project));
          }
        }

        scheduleForNextResolve(toResolve);

        fireProjectScheduled();
      }

      private boolean haveChanges(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
        for (MavenProjectChanges each : MavenUtil.collectSeconds(projectsWithChanges)) {
          if (each.hasChanges()) return true;
        }
        return false;
      }

      @Override
      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  @Nullable NativeMavenProjectHolder nativeMavenProject) {
        if (nativeMavenProject != null) {
          if (shouldScheduleProject(projectWithChanges)) {
            scheduleForNextImport(projectWithChanges);

            MavenImportingSettings importingSettings;

            AccessToken token = ReadAction.start();
            try {
              if (myProject.isDisposed()) return;
              importingSettings = getImportingSettings();
            }
            finally {
              token.finish();
            }

            scheduleArtifactsDownloading(Collections.singleton(projectWithChanges.first),
                                         null,
                                         importingSettings.isDownloadSourcesAutomatically(),
                                         importingSettings.isDownloadDocsAutomatically(),
                                         null);
          }

          if (!projectWithChanges.first.hasReadingProblems() && projectWithChanges.first.hasUnresolvedPlugins()) {
            schedulePluginsResolve(projectWithChanges.first, nativeMavenProject);
          }
        }
      }

      @Override
      public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        if (shouldScheduleProject(projectWithChanges)) {
          scheduleForNextImport(projectWithChanges);
        }
      }

      private boolean shouldScheduleProject(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        return !projectWithChanges.first.hasReadingProblems() && projectWithChanges.second.hasChanges();
      }
    });
  }

  public void listenForExternalChanges() {
    myWatcher.start();
  }

  @Override
  public void projectClosed() {
    synchronized (isInitialized) {
      if (!isInitialized.getAndSet(false)) return;

      Disposer.dispose(myImportingQueue);

      myWatcher.stop();

      myReadingProcessor.stop();
      myResolvingProcessor.stop();
      myPluginsResolvingProcessor.stop();
      myFoldersResolvingProcessor.stop();
      myArtifactsDownloadingProcessor.stop();
      myPostProcessor.stop();

      if (isUnitTestMode()) {
        FileUtil.delete(getProjectsTreesDir());
      }
    }
  }

  public MavenEmbeddersManager getEmbeddersManager() {
    return myEmbeddersManager;
  }

  private boolean isInitialized() {
    return isInitialized.get();
  }

  public boolean isMavenizedProject() {
    return isInitialized();
  }

  public boolean isMavenizedModule(final Module m) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      return !m.isDisposed() && "true".equals(m.getOptionValue(getMavenizedModuleOptionName()));
    }
    finally {
      accessToken.finish();
    }
  }

  public void setMavenizedModules(Collection<Module> modules, boolean mavenized) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    for (Module m : modules) {
      if (m.isDisposed()) continue;

      if (mavenized) {
        m.setOption(getMavenizedModuleOptionName(), "true");

        // clear external system API options
        // see com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService#setModuleOptions
        m.clearOption("external.system.id");
        m.clearOption("external.linked.project.path");
        m.clearOption("external.root.project.path");
        m.clearOption("external.system.module.group");
        m.clearOption("external.system.module.version");
      }
      else {
        m.clearOption(getMavenizedModuleOptionName());
      }
    }
  }

  private static String getMavenizedModuleOptionName() {
    return "org.jetbrains.idea.maven.project.MavenProjectsManager.isMavenModule";
  }

  @TestOnly
  public void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles profiles) {
    myWatcher.resetManagedFilesAndProfilesInTests(files, profiles);
  }

  public void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles) {
    if (!isInitialized()) {
      initNew(files, profiles);
    }
    else {
      myWatcher.addManagedFilesWithProfiles(files, profiles);
    }
  }

  public void addManagedFiles(@NotNull List<VirtualFile> files) {
    addManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE);
  }

  public void addManagedFilesOrUnignore(@NotNull List<VirtualFile> files) {
    removeIgnoredFilesPaths(MavenUtil.collectPaths(files));
    addManagedFiles(files);
  }

  public void removeManagedFiles(@NotNull List<VirtualFile> files) {
    myWatcher.removeManagedFiles(files);
  }

  public boolean isManagedFile(@NotNull VirtualFile f) {
    if (!isInitialized()) return false;
    return myProjectsTree.isManagedFile(f);
  }

  @NotNull
  public MavenExplicitProfiles getExplicitProfiles() {
    if (!isInitialized()) return MavenExplicitProfiles.NONE;
    return myProjectsTree.getExplicitProfiles();
  }

  public void setExplicitProfiles(@NotNull MavenExplicitProfiles profiles) {
    myWatcher.setExplicitProfiles(profiles);
  }

  @NotNull
  public Collection<String> getAvailableProfiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getAvailableProfiles();
  }

  @NotNull
  public Collection<Pair<String, MavenProfileKind>> getProfilesWithStates() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProfilesWithStates();
  }

  public boolean hasProjects() {
    if (!isInitialized()) return false;
    return myProjectsTree.hasProjects();
  }

  @NotNull
  public List<MavenProject> getProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProjects();
  }

  @NotNull
  public List<MavenProject> getRootProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getRootProjects();
  }

  @NotNull
  public List<MavenProject> getNonIgnoredProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getNonIgnoredProjects();
  }

  @NotNull
  public List<VirtualFile> getProjectsFiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProjectsFiles();
  }

  @Nullable
  public MavenProject findProject(@NotNull VirtualFile f) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(f);
  }

  @Nullable
  public MavenProject findProject(@NotNull MavenId id) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(id);
  }

  @Nullable
  public MavenProject findProject(@NotNull MavenArtifact artifact) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(artifact);
  }

  @Nullable
  public MavenProject findProject(@NotNull Module module) {
    VirtualFile f = findPomFile(module, new MavenModelsProvider() {
      public Module[] getModules() {
        throw new UnsupportedOperationException();
      }

      public VirtualFile[] getContentRoots(Module module) {
        return ModuleRootManager.getInstance(module).getContentRoots();
      }
    });
    return f == null ? null : findProject(f);
  }

  @Nullable
  public Module findModule(@NotNull MavenProject project) {
    if (!isInitialized()) return null;
    return ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(project.getFile());
  }

  @NotNull
  public Collection<MavenProject> findInheritors(@Nullable MavenProject parent) {
    if (parent == null || !isInitialized()) return Collections.emptyList();
    return myProjectsTree.findInheritors(parent);
  }

  @Nullable
  public MavenProject findContainingProject(@NotNull VirtualFile file) {
    if (!isInitialized()) return null;
    Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
    return module == null ? null : findProject(module);
  }

  @Nullable
  private static VirtualFile findPomFile(@NotNull Module module, @NotNull MavenModelsProvider modelsProvider) {
    for (VirtualFile root : modelsProvider.getContentRoots(module)) {
      final VirtualFile virtualFile = root.findChild(MavenConstants.POM_XML);
      if (virtualFile != null) {
        return virtualFile;
      }
    }
    return null;
  }

  @Nullable
  public MavenProject findAggregator(@NotNull MavenProject module) {
    if (!isInitialized()) return null;
    return myProjectsTree.findAggregator(module);
  }

  @NotNull
  public List<MavenProject> getModules(@NotNull MavenProject aggregator) {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getModules(aggregator);
  }

  @NotNull
  public List<String> getIgnoredFilesPaths() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getIgnoredFilesPaths();
  }

  public void setIgnoredFilesPaths(@NotNull List<String> paths) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredFilesPaths(paths);
  }

  public void removeIgnoredFilesPaths(final Collection<String> paths) {
    if (!isInitialized()) return;
    myProjectsTree.removeIgnoredFilesPaths(paths);
  }

  public boolean getIgnoredState(@NotNull MavenProject project) {
    if (!isInitialized()) return false;
    return myProjectsTree.getIgnoredState(project);
  }

  public void setIgnoredState(@NotNull List<MavenProject> projects, boolean ignored) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredState(projects, ignored);
  }

  @NotNull
  public List<String> getIgnoredFilesPatterns() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getIgnoredFilesPatterns();
  }

  public void setIgnoredFilesPatterns(@NotNull List<String> patterns) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredFilesPatterns(patterns);
  }

  public boolean isIgnored(@NotNull MavenProject project) {
    if (!isInitialized()) return false;
    return myProjectsTree.isIgnored(project);
  }

  public Set<MavenRemoteRepository> getRemoteRepositories() {
    Set<MavenRemoteRepository> result = new THashSet<MavenRemoteRepository>();
    for (MavenProject each : getProjects()) {
      for (MavenRemoteRepository eachRepository : each.getRemoteRepositories()) {
        result.add(eachRepository);
      }
    }
    return result;
  }

  @TestOnly
  public MavenProjectsTree getProjectsTreeForTests() {
    return myProjectsTree;
  }

  private void scheduleUpdateAllProjects(boolean forceImportAndResolve) {
    doScheduleUpdateProjects(null, false, forceImportAndResolve);
  }

  public void forceUpdateProjects(@NotNull Collection<MavenProject> projects) {
    doScheduleUpdateProjects(projects, true, true);
  }

  public void forceUpdateAllProjectsOrFindAllAvailablePomFiles() {
    if (!isMavenizedProject()) {
      addManagedFiles(collectAllAvailablePomFiles());
    }
    doScheduleUpdateProjects(null, true, true);
  }

  private void doScheduleUpdateProjects(final Collection<MavenProject> projects,
                                        final boolean forceUpdate,
                                        final boolean forceImportAndResolve) {
    MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
      public void run() {
        if (projects == null) {
          myWatcher.scheduleUpdateAll(forceUpdate, forceImportAndResolve);
        }
        else {
          myWatcher.scheduleUpdate(MavenUtil.collectFiles(projects),
                                   Collections.<VirtualFile>emptyList(),
                                   forceUpdate,
                                   forceImportAndResolve);
        }
      }
    });
  }

  public void scheduleImportAndResolve() {
    scheduleResolve();  // scheduleImport will be called after the scheduleResolve process has finished
    fireImportAndResolveScheduled();
  }

  private void scheduleResolve() {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        LinkedHashSet<MavenProject> toResolve;
        synchronized (myImportingDataLock) {
          toResolve = new LinkedHashSet<MavenProject>(myProjectsToResolve);
          myProjectsToResolve.clear();
        }
        final ResolveContext context = new ResolveContext();

        Iterator<MavenProject> it = toResolve.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          Runnable onCompletion = it.hasNext() ? null : new Runnable() {
            @Override
            public void run() {
              if (hasScheduledProjects()) scheduleImport();
            }
          };

          myResolvingProcessor.scheduleTask(
            new MavenProjectsProcessorResolvingTask(each, myProjectsTree, getGeneralSettings(), onCompletion, context));
        }
      }
    });
  }

  public void evaluateEffectivePom(@NotNull final MavenProject mavenProject, @NotNull final NullableConsumer<String> consumer) {
    runWhenFullyOpen(new Runnable() {
      @Override
      public void run() {
        myResolvingProcessor.scheduleTask(new MavenProjectsProcessorTask() {
          @Override
          public void perform(Project project,
                              MavenEmbeddersManager embeddersManager,
                              MavenConsole console,
                              MavenProgressIndicator indicator)
            throws MavenProcessCanceledException {

            indicator.setText("Evaluating effective POM");

            myProjectsTree.executeWithEmbedder(mavenProject,
                                               getEmbeddersManager(),
                                               MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE,
                                               console,
                                               indicator,
                                               new MavenProjectsTree.EmbedderTask() {
                                                 @Override
                                                 public void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
                                                   try {
                                                     MavenExplicitProfiles profiles = mavenProject.getActivatedProfilesIds();
                                                     String res =
                                                       embedder.evaluateEffectivePom(mavenProject.getFile(), profiles.getEnabledProfiles(),
                                                                                     profiles.getDisabledProfiles());
                                                     consumer.consume(res);
                                                   }
                                                   catch (UnsupportedOperationException e) {
                                                     consumer.consume(null); // null means UnsupportedOperationException
                                                   }
                                                 }
                                               });
          }
        });
      }
    });
  }

  @TestOnly
  public void scheduleResolveInTests(Collection<MavenProject> projects) {
    scheduleForNextResolve(projects);
    scheduleResolve();
  }

  @TestOnly
  public void scheduleResolveAllInTests() {
    scheduleResolveInTests(getProjects());
  }

  public void scheduleFoldersResolve(final Collection<MavenProject> projects) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        Iterator<MavenProject> it = projects.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          Runnable onCompletion = it.hasNext() ? null : new Runnable() {
            @Override
            public void run() {
              if (hasScheduledProjects()) scheduleImport();
            }
          };
          myFoldersResolvingProcessor.scheduleTask(
            new MavenProjectsProcessorFoldersResolvingTask(each, getImportingSettings(), myProjectsTree, onCompletion));
        }
      }
    });
  }

  public void scheduleFoldersResolveForAllProjects() {
    scheduleFoldersResolve(getProjects());
  }

  private void schedulePluginsResolve(final MavenProject project, final NativeMavenProjectHolder nativeMavenProject) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        myPluginsResolvingProcessor
          .scheduleTask(new MavenProjectsProcessorPluginsResolvingTask(project, nativeMavenProject, myProjectsTree));
      }
    });
  }

  public void scheduleArtifactsDownloading(final Collection<MavenProject> projects,
                                           @Nullable final Collection<MavenArtifact> artifacts,
                                           final boolean sources, final boolean docs,
                                           @Nullable final AsyncResult<MavenArtifactDownloader.DownloadResult> result) {
    if (!sources && !docs) return;

    runWhenFullyOpen(new Runnable() {
      public void run() {
        myArtifactsDownloadingProcessor
          .scheduleTask(new MavenProjectsProcessorArtifactsDownloadingTask(projects, artifacts, myProjectsTree, sources, docs, result));
      }
    });
  }

  private void scheduleImportSettings() {
    scheduleImportSettings(false);
  }

  private void scheduleImportSettings(boolean importModuleGroupsRequired) {
    synchronized (myImportingDataLock) {
      myImportModuleGroupsRequired = importModuleGroupsRequired;
    }
    scheduleImport();
  }

  private void scheduleImport() {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        myImportingQueue.queue(new Update(MavenProjectsManager.this) {
          public void run() {
            importProjects();
          }
        });
      }
    });
  }

  @TestOnly
  public void scheduleImportInTests(List<VirtualFile> projectFiles) {
    List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<Pair<MavenProject, MavenProjectChanges>>();
    for (VirtualFile each : projectFiles) {
      MavenProject project = findProject(each);
      if (project != null) {
        toImport.add(Pair.create(project, MavenProjectChanges.ALL));
      }
    }
    scheduleForNextImport(toImport);
    scheduleImport();
  }

  private void scheduleForNextImport(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    scheduleForNextImport(Collections.singletonList(projectWithChanges));
  }

  private void scheduleForNextImport(Collection<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
    synchronized (myImportingDataLock) {
      for (Pair<MavenProject, MavenProjectChanges> each : projectsWithChanges) {
        MavenProjectChanges changes = each.second.mergedWith(myProjectsToImport.get(each.first));
        myProjectsToImport.put(each.first, changes);
      }
    }
  }

  private void scheduleForNextResolve(Collection<MavenProject> projects) {
    synchronized (myImportingDataLock) {
      myProjectsToResolve.addAll(projects);
    }
  }

  public boolean hasScheduledProjects() {
    if (!isInitialized()) return false;
    synchronized (myImportingDataLock) {
      return !myProjectsToImport.isEmpty() || !myProjectsToResolve.isEmpty();
    }
  }

  @TestOnly
  public boolean hasScheduledImportsInTests() {
    if (!isInitialized()) return false;
    return !myImportingQueue.isEmpty();
  }

  @TestOnly
  public void performScheduledImportInTests() {
    if (!isInitialized()) return;
    runWhenFullyOpen(new Runnable() {
      public void run() {
        myImportingQueue.flush(false);
      }
    });
  }

  private void runWhenFullyOpen(final Runnable runnable) {
    if (!isInitialized()) return; // may be called from scheduleImport after project started closing and before it is closed.

    if (isNoBackgroundMode()) {
      runnable.run();
      return;
    }

    final Ref<Runnable> wrapper = new Ref<Runnable>();
    wrapper.set(new Runnable() {
      public void run() {
        if (!StartupManagerEx.getInstanceEx(myProject).postStartupActivityPassed()) {
          myInitializationAlarm.addRequest(new Runnable() { // should not remove previously schedules tasks

            public void run() {
              wrapper.get().run();
            }
          }, 1000);
          return;
        }
        runnable.run();
      }
    });
    MavenUtil.runWhenInitialized(myProject, wrapper.get());
  }

  private void schedulePostImportTasks(List<MavenProjectsProcessorTask> postTasks) {
    for (MavenProjectsProcessorTask each : postTasks) {
      myPostProcessor.scheduleTask(each);
    }
  }

  private void unscheduleAllTasks(List<MavenProject> projects) {
    for (MavenProject each : projects) {
      MavenProjectsProcessorEmptyTask dummyTask = new MavenProjectsProcessorEmptyTask(each);

      synchronized (myImportingDataLock) {
        myProjectsToImport.remove(each);
        myProjectsToResolve.remove(each);
      }

      myResolvingProcessor.removeTask(dummyTask);
      myPluginsResolvingProcessor.removeTask(dummyTask);
      myFoldersResolvingProcessor.removeTask(dummyTask);
      myPostProcessor.removeTask(dummyTask);
    }
  }

  @TestOnly
  public void unscheduleAllTasksInTests() {
    unscheduleAllTasks(getProjects());
  }

  public void waitForReadingCompletion() {
    waitForTasksCompletion(null);
  }

  public void waitForResolvingCompletion() {
    waitForTasksCompletion(myResolvingProcessor);
  }

  public void waitForFoldersResolvingCompletion() {
    waitForTasksCompletion(myFoldersResolvingProcessor);
  }

  public void waitForPluginsResolvingCompletion() {
    waitForTasksCompletion(myPluginsResolvingProcessor);
  }

  public void waitForArtifactsDownloadingCompletion() {
    waitForTasksCompletion(myArtifactsDownloadingProcessor);
  }

  public void waitForPostImportTasksCompletion() {
    myPostProcessor.waitForCompletion();
  }

  private void waitForTasksCompletion(MavenProjectsProcessor processor) {
    FileDocumentManager.getInstance().saveAllDocuments();

    myReadingProcessor.waitForCompletion();
    if (processor != null) processor.waitForCompletion();
  }

  public void updateProjectTargetFolders() {
    updateProjectFolders(true);
  }

  private void updateProjectFolders(final boolean targetFoldersOnly) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;

        MavenFoldersImporter.updateProjectFolders(myProject, targetFoldersOnly);
        VirtualFileManager.getInstance().asyncRefresh(null);
      }
    });
  }

  public List<Module> importProjects() {
    return importProjects(new MavenDefaultModifiableModelsProvider(myProject));
  }

  public List<Module> importProjects(final MavenModifiableModelsProvider modelsProvider) {
    final Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges;
    final boolean importModuleGroupsRequired;
    synchronized (myImportingDataLock) {
      projectsToImportWithChanges = new LinkedHashMap<MavenProject, MavenProjectChanges>(myProjectsToImport);
      myProjectsToImport.clear();
      importModuleGroupsRequired = myImportModuleGroupsRequired;
      myImportModuleGroupsRequired = false;
    }

    final Ref<MavenProjectImporter> importer = new Ref<MavenProjectImporter>();
    final Ref<List<MavenProjectsProcessorTask>> postTasks = new Ref<List<MavenProjectsProcessorTask>>();

    final Runnable r = new Runnable() {
      public void run() {
        MavenProjectImporter projectImporter = new MavenProjectImporter(myProject,
                                                                        myProjectsTree,
                                                                        getFileToModuleMapping(modelsProvider),
                                                                        projectsToImportWithChanges,
                                                                        importModuleGroupsRequired,
                                                                        modelsProvider,
                                                                        getImportingSettings());
        importer.set(projectImporter);
        postTasks.set(projectImporter.importProject());
      }
    };

    // called from wizard or ui
    if (ApplicationManager.getApplication().isDispatchThread()) {
      r.run();
    }
    else {
      MavenUtil.runInBackground(myProject, ProjectBundle.message("maven.project.importing"), false, new MavenTask() {
        public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
          r.run();
        }
      }).waitFor();
    }


    VirtualFileManager fm = VirtualFileManager.getInstance();
    if (isNormalProject()) {
      fm.asyncRefresh(null);
    }
    else {
      fm.syncRefresh();
    }

    if (postTasks.get() != null /*may be null if importing is cancelled*/) {
      schedulePostImportTasks(postTasks.get());
    }

    // do not block user too often
    myImportingQueue.restartTimer();

    MavenProjectImporter projectImporter = importer.get();
    if (projectImporter == null) return Collections.emptyList();

    return projectImporter.getCreatedModules();
  }

  private Map<VirtualFile, Module> getFileToModuleMapping(MavenModelsProvider modelsProvider) {
    Map<VirtualFile, Module> result = new THashMap<VirtualFile, Module>();
    for (Module each : modelsProvider.getModules()) {
      VirtualFile f = findPomFile(each, modelsProvider);
      if (f != null) result.put(f, each);
    }
    return result;
  }

  private List<VirtualFile> collectAllAvailablePomFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>(getFileToModuleMapping(new MavenDefaultModelsProvider(myProject)).keySet());

    VirtualFile pom = myProject.getBaseDir().findChild(MavenConstants.POM_XML);
    if (pom != null) result.add(pom);

    return result;
  }

  public void addManagerListener(Listener listener) {
    myManagerListeners.add(listener);
  }

  public void addProjectsTreeListener(MavenProjectsTree.Listener listener) {
    myProjectsTreeDispatcher.addListener(listener);
  }

  @TestOnly
  public void fireActivatedInTests() {
    fireActivated();
  }

  private void fireActivated() {
    for (Listener each : myManagerListeners) {
      each.activated();
    }
  }

  private void fireProjectScheduled() {
    for (Listener each : myManagerListeners) {
      each.projectsScheduled();
    }
  }

  private void fireImportAndResolveScheduled() {
    for (Listener each : myManagerListeners) {
      each.importAndResolveScheduled();
    }
  }

  public interface Listener {
    void activated();

    void projectsScheduled();

    void importAndResolveScheduled();
  }
}
