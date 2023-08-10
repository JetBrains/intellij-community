// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.SyncViewManager;
import com.intellij.configurationStore.SettingsSavingComponentJavaAdapter;
import com.intellij.ide.impl.ProjectUtilKt;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.buildtool.MavenImportSpec;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.CacheForCompilerErrorMessages;
import org.jetbrains.idea.maven.importing.MavenImportUtil;
import org.jetbrains.idea.maven.importing.MavenPomPathModuleService;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.auto.reload.MavenProjectManagerWatcher;
import org.jetbrains.idea.maven.project.importing.FilesList;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.jetbrains.idea.maven.project.importing.MavenProjectManagerListenerToBusBridge;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@State(name = "MavenProjectsManager")
public abstract class MavenProjectsManager extends MavenSimpleProjectComponent
  implements PersistentStateComponent<MavenProjectsManagerState>, SettingsSavingComponentJavaAdapter, Disposable, MavenAsyncProjectsManager {
  private static final int IMPORT_DELAY = 1000;

  private final ReentrantLock initLock = new ReentrantLock();
  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

  private final Alarm myInitializationAlarm;

  private final MavenEmbeddersManager myEmbeddersManager;

  private MavenProjectsTree myProjectsTree;
  private MavenProjectManagerWatcher myWatcher;

  protected MavenMergingUpdateQueue myImportingQueue;

  private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher =
    EventDispatcher.create(MavenProjectsTree.Listener.class);
  private final List<Listener> myManagerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ModificationTracker myModificationTracker;
  protected BuildProgressListener myProgressListener;

  private MavenWorkspaceSettings myWorkspaceSettings;

  private final AtomicReference<MavenSyncConsole> mySyncConsole = new AtomicReference<>();
  private final MavenMergingUpdateQueue mySaveQueue;
  private static final int SAVE_DELAY = 1000;
  protected Module myPreviewModule;
  private transient boolean forceUpdateSnapshots = false;

  public static MavenProjectsManager getInstance(@NotNull Project project) {
    return project.getService(MavenProjectsManager.class);
  }

  @Nullable
  public static MavenProjectsManager getInstanceIfCreated(@NotNull Project project) {
    return project.getServiceIfCreated(MavenProjectsManager.class);
  }

  public MavenProjectsManager(@NotNull Project project) {
    super(project);

    myEmbeddersManager = new MavenEmbeddersManager(project);
    myModificationTracker = new MavenModificationTracker(this);
    myInitializationAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    mySaveQueue = new MavenMergingUpdateQueue("Maven save queue", SAVE_DELAY, !MavenUtil.isMavenUnitTestModeEnabled(), this);
    myProgressListener = myProject.getService(SyncViewManager.class);
    MavenRehighlighter.install(project, this);
    Disposer.register(this, this::projectClosed);
    CacheForCompilerErrorMessages.connectToJdkListener(project, this);
  }

  @ApiStatus.Internal
  public abstract void waitForPluginResolution();

  @TestOnly
  public void setProgressListener(BuildProgressListener testViewManager) {
    myProgressListener = testViewManager;
  }

  @TestOnly
  public BuildProgressListener getProgressListener() {
    return myProgressListener;
  }

  @Override
  public MavenProjectsManagerState getState() {
    if (isInitialized()) {
      applyTreeToState();
    }
    return myState;
  }

  @Override
  public void loadState(@NotNull MavenProjectsManagerState state) {
    myState = state;
    if (isInitialized()) {
      applyStateToTree(myProjectsTree, this);
      scheduleUpdateAllProjects(new MavenImportSpec(false, false, false));
    }
  }

  @Override
  public void dispose() {
    mySyncConsole.set(null);
    myManagerListeners.clear();
  }

  public static void setupCreatedMavenProject(@NotNull Project project) {
    setupCreatedMavenProject(getInstance(project).getImportingSettings());
  }

  public static void setupCreatedMavenProject(@NotNull MavenImportingSettings settings) {
    settings.setWorkspaceImportEnabled(true);
  }

  public ModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  public MavenGeneralSettings getGeneralSettings() {
    MavenGeneralSettings generalSettings = getWorkspaceSettings().getGeneralSettings();
    generalSettings.setProject(myProject);
    return generalSettings;
  }

  public MavenImportingSettings getImportingSettings() {
    return getWorkspaceSettings().getImportingSettings();
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

  @ApiStatus.Internal
  public int getFilterConfigCrc(@NotNull ProjectFileIndex projectFileIndex) {
    return myProjectsTree.getFilterConfigCrc(projectFileIndex);
  }


  @Override
  public void initializeComponent() {
    //noinspection deprecation
    ProjectUtilKt.executeOnPooledThread(myProject, () -> {
      tryInit();
    });
  }

  @TestOnly
  public void initForTests() {
    init();
  }

  private void tryInit() {
    if (!isNormalProject()) {
      return;
    }
    boolean wasMavenized = !myState.originalFiles.isEmpty();
    if (!wasMavenized) {
      return;
    }
    init();
  }

  private void init() {
    doInit(false);
    if (!MavenUtil.isLinearImportEnabled()) {
      scheduleUpdateAll(new MavenImportSpec(false, false, false));
    }
  }

  private void doInit(final boolean isNew) {
    initLock.lock();
    try {
      if (isInitialized.getAndSet(true)) {
        return;
      }
      initManagerListenerToBusBridge();
      initBusToManagerListenerBridge();
      initPreloadMavenServices();
      initProjectsTree(!isNew);
      initWorkers();
      listenForSettingsChanges();
      listenForProjectsTreeChanges();
      registerSyncConsoleListener();
      updateTabTitles();
      if (isNew) {
        doActivate();
      }
    }
    finally {
      initLock.unlock();
    }
  }

  private void doActivate() {
    fireActivated();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      listenForExternalChanges();
      MavenIndicesManager.getInstance(myProject).scheduleUpdateIndicesList(null);
    }
  }

  protected void onProjectStartup() {
    if (isInitialized()) {
      doActivate();
    }
  }

  private void initBusToManagerListenerBridge() {
    if (!MavenUtil.isLinearImportEnabled()) return;
    myProject.getMessageBus().connect(this).subscribe(MavenImportingManager.LEGACY_PROJECT_MANAGER_LISTENER, new Listener() {
      @Override
      public void activated() {
        fireActivated();
      }

      @Override
      public void importAndResolveScheduled() {
        for (Listener each : myManagerListeners) {
          each.importAndResolveScheduled();
        }
      }

      @Override
      public void projectImportCompleted() {
        fireProjectImportCompleted();
      }
    });
  }

  private void initManagerListenerToBusBridge() {
    if (MavenUtil.isLinearImportEnabled()) return;
    addManagerListener(new MavenProjectManagerListenerToBusBridge(myProject), this);
  }

  private void initPreloadMavenServices() {
    // init maven tool window
    MavenProjectsNavigator.getInstance(myProject);
    // add CompileManager before/after tasks
    MavenTasksManager.getInstance(myProject);
    // init maven shortcuts manager to subscribe to KeymapManagerListener
    MavenShortcutsManager.getInstance(myProject);
  }

  private void updateTabTitles() {
    Application app = ApplicationManager.getApplication();
    if (MavenUtil.isMavenUnitTestModeEnabled() || app.isHeadlessEnvironment()) {
      return;
    }

    addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
        updateTabName(MavenUtil.collectFirsts(updated), myProject);
      }
    });
  }

  private static void updateTabName(@NotNull List<MavenProject> projects, @NotNull Project project) {
    MavenUtil.invokeLater(project, () -> {
      for (MavenProject each : projects) {
        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(each.getFile());
      }
    });
  }

  public MavenSyncConsole getSyncConsole() {
    if (null == mySyncConsole.get()) {
      mySyncConsole.compareAndSet(null, new MavenSyncConsole(myProject));
    }
    return mySyncConsole.get();
  }

  @NotNull
  private MavenProjectsTree initProjectsTree(boolean tryToLoadExisting) {
    if (tryToLoadExisting) {
      Path file = getProjectsTreeFile();
      try {
        if (Files.exists(file)) {
          myProjectsTree = MavenProjectsTree.read(myProject, file);
        }
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }

    if (myProjectsTree == null) myProjectsTree = new MavenProjectsTree(myProject);
    applyStateToTree(myProjectsTree, this);
    myProjectsTree.addListener(myProjectsTreeDispatcher.getMulticaster(), this);
    return myProjectsTree;
  }

  private void applyTreeToState() {
    myState.originalFiles = myProjectsTree.getManagedFilesPaths();
    myState.ignoredFiles = new HashSet<>(myProjectsTree.getIgnoredFilesPaths());
    myState.ignoredPathMasks = myProjectsTree.getIgnoredFilesPatterns();
  }

  public static void applyStateToTree(MavenProjectsTree tree, MavenProjectsManager manager) {
    MavenWorkspaceSettings settings = manager.getWorkspaceSettings();
    MavenExplicitProfiles explicitProfiles = new MavenExplicitProfiles(settings.enabledProfiles, settings.disabledProfiles);
    tree.resetManagedFilesPathsAndProfiles(manager.myState.originalFiles, explicitProfiles);
    tree.setIgnoredFilesPaths(new ArrayList<>(manager.myState.ignoredFiles));
    tree.setIgnoredFilesPatterns(manager.myState.ignoredPathMasks);
  }

  @Override
  public void doSave() {
    if (myProjectsTree == null) {
      return;
    }

    mySaveQueue.queue(new Update(this) {
      @Override
      public void run() {
        try {
          myProjectsTree.save(getProjectsTreeFile());
        }
        catch (IOException e) {
          MavenLog.LOG.info(e);
        }
      }
    });
  }

  @ApiStatus.Internal
  public Path getProjectsTreeFile() {
    return getProjectsTreesDir().resolve(myProject.getLocationHash()).resolve("tree.dat");
  }

  @NotNull
  @ApiStatus.Internal
  public static Path getProjectsTreesDir() {
    return MavenUtil.getPluginSystemDir("Projects");
  }

  private void initWorkers() {
    myWatcher = new MavenProjectManagerWatcher(myProject, myProjectsTree);

    myImportingQueue =
      new MavenMergingUpdateQueue(getClass().getName() + ": Importing queue", IMPORT_DELAY, !MavenUtil.isMavenUnitTestModeEnabled(), this);

    myImportingQueue.makeUserAware(myProject);
    myImportingQueue.makeModalAware(myProject);
  }

  protected abstract void listenForSettingsChanges();

  private void registerSyncConsoleListener() {
    if (MavenUtil.isLinearImportEnabled()) return;
    myProjectsTreeDispatcher.addListener(new MavenProjectsTree.Listener() {
      @Override
      public void pluginsResolved(@NotNull MavenProject project) {
        getSyncConsole().getListener(MavenServerConsoleIndicator.ResolveType.PLUGIN).finish();
      }

      @Override
      public void artifactsDownloaded(@NotNull MavenProject project) {
        getSyncConsole().getListener(MavenServerConsoleIndicator.ResolveType.DEPENDENCY).finish();
      }
    });
  }

  private void listenForProjectsTreeChanges() {
    if (MavenUtil.isLinearImportEnabled()) return;
    myProjectsTreeDispatcher.addListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsIgnoredStateChanged(@NotNull List<MavenProject> ignored,
                                              @NotNull List<MavenProject> unignored,
                                              boolean fromImport) {
        if (!fromImport) scheduleImportChangedProjects();
      }
    }, this);
  }

  public void listenForExternalChanges() {
    myWatcher.start();
  }

  @TestOnly
  public void enableAutoImportInTests() {
    assert isInitialized();
    listenForExternalChanges();
    myWatcher.enableAutoImportInTests();
  }

  private void projectClosed() {
    initLock.lock();
    try {
      if (!isInitialized.getAndSet(false)) {
        return;
      }

      if (myImportingQueue != null) {
        Disposer.dispose(myImportingQueue);
      }

      myWatcher.stop();

      mySaveQueue.flush();

      if (MavenUtil.isMavenUnitTestModeEnabled()) {
        PathKt.delete(getProjectsTreesDir());
      }
    }
    finally {
      initLock.unlock();
    }
  }

  public MavenEmbeddersManager getEmbeddersManager() {
    return myEmbeddersManager;
  }

  private boolean isInitialized() {
    return !initLock.isLocked() && isInitialized.get();
  }

  public boolean isMavenizedProject() {
    return isInitialized();
  }

  public boolean isMavenizedModule(@NotNull Module m) {
    return MavenUtil.isMavenizedModule(m);
  }

  public void addManagedFilesWithProfiles(final List<VirtualFile> files, MavenExplicitProfiles profiles, Module previewModuleToDelete) {
    doAddManagedFilesWithProfiles(files, profiles, previewModuleToDelete);
    scheduleUpdateAll(new MavenImportSpec(false, true, false));
  }

  protected void doAddManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles, Module previewModuleToDelete) {
    myPreviewModule = previewModuleToDelete;
    if (!isInitialized()) {
      myState.originalFiles = MavenUtil.collectPaths(files);
      MavenWorkspaceSettings workspaceSettings = getWorkspaceSettings();
      workspaceSettings.setEnabledProfiles(profiles.getEnabledProfiles());
      workspaceSettings.setDisabledProfiles(profiles.getDisabledProfiles());
      doInit(true);
    }
    else {
      myProjectsTree.addManagedFilesWithProfiles(files, profiles);
    }
  }

  public void addManagedFiles(@NotNull List<VirtualFile> files) {
    addManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE, null);
  }

  public void addManagedFilesOrUnignore(@NotNull List<VirtualFile> files) {
    removeIgnoredFilesPaths(MavenUtil.collectPaths(files));
    addManagedFiles(files);
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


  public MavenProject findSingleProjectInReactor(@NotNull MavenId id) {
    if (!isInitialized()) return null;
    return myProjectsTree.findSingleProjectInReactor(id);
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
    MavenProject mavenProject = getMavenProject(module);
    String moduleName = module.getName();
    if (mavenProject == null && MavenImportUtil.isMainOrTestSubmodule(moduleName)) {
      Module parentModule = ModuleManager.getInstance(myProject).findModuleByName(MavenImportUtil.getParentModuleName(moduleName));
      mavenProject = parentModule != null ? getMavenProject(parentModule) : null;
    }
    return mavenProject;
  }

  private MavenProject getMavenProject(@NotNull Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      VirtualFile f = findPomFile(module, new MavenModelsProvider() {
        @Override
        public Module[] getModules() {
          throw new UnsupportedOperationException();
        }

        @Override
        public VirtualFile[] getContentRoots(Module module) {
          return ModuleRootManager.getInstance(module).getContentRoots();
        }
      });
      MavenProject result = f == null ? null : findProject(f);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @RequiresReadLock
  public @Nullable Module findModule(@NotNull MavenProject project) {
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
  private VirtualFile findPomFile(@NotNull Module module, @NotNull MavenModelsProvider modelsProvider) {
    String pomFileUrl = MavenPomPathModuleService.getInstance(module).getPomFileUrl();
    if (pomFileUrl != null) {
      return VirtualFileManager.getInstance().findFileByUrl(pomFileUrl);
    }
    for (VirtualFile root : modelsProvider.getContentRoots(module)) {
      List<VirtualFile> pomFiles = MavenUtil.streamPomFiles(module.getProject(), root).toList();
      if (pomFiles.isEmpty()) {
        continue;
      }

      if (pomFiles.size() == 1) {
        return pomFiles.get(0);
      }

      for (VirtualFile file : pomFiles) {
        if (module.getName().equals(file.getNameWithoutExtension())) {
          return file;
        }
        MavenProject mavenProject = findProject(file);
        if (mavenProject != null) {
          if (module.getName().equals(mavenProject.getMavenId().getArtifactId())) {
            return file;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public MavenProject findAggregator(@NotNull MavenProject mavenProject) {
    if (!isInitialized()) return null;
    return myProjectsTree.findAggregator(mavenProject);
  }

  @Nullable
  public MavenProject findRootProject(@NotNull MavenProject mavenProject) {
    if (!isInitialized()) return null;
    return myProjectsTree.findRootProject(mavenProject);
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

  @ApiStatus.Internal
  public void setIgnoredStateForPoms(@NotNull List<String> pomPaths, boolean ignored) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredStateForPoms(pomPaths, ignored);
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
    Set<MavenRemoteRepository> result = new HashSet<>();
    for (MavenProject each : getProjects()) {
      result.addAll(each.getRemoteRepositories());
    }
    return result;
  }

  @TestOnly
  public MavenProjectsTree getProjectsTreeForTests() {
    return myProjectsTree;
  }

  @ApiStatus.Internal
  public void setProjectsTree(@NotNull MavenProjectsTree newTree) {
    if (!isInitialized()) {
      doInit(true);
      if (!MavenUtil.isLinearImportEnabled()) {
        scheduleUpdateAll(new MavenImportSpec(false, true, false));
      }
    }
    newTree.addListenersFrom(myProjectsTree);
    myProjectsTree = newTree;
    myWatcher.setProjectTree(newTree);
  }

  @ApiStatus.Internal
  public EventDispatcher<MavenProjectsTree.Listener> getTreeListenerEventDispatcher() {
    return myProjectsTreeDispatcher;
  }

  @ApiStatus.Internal
  @NotNull
  public MavenProjectsTree getProjectsTree() {
    if (myProjectsTree == null) {
      return initProjectsTree(true);
    }
    return myProjectsTree;
  }

  @TestOnly
  public synchronized void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myProjectsTree.resetManagedFilesAndProfiles(files, explicitProfiles);
    scheduleUpdateAll(new MavenImportSpec(true, true, true));
  }

  public synchronized void removeManagedFiles(List<VirtualFile> files) {
    myProjectsTree.removeManagedFiles(files);
    scheduleUpdateAll(new MavenImportSpec(false, true, true));
  }

  public synchronized void setExplicitProfiles(MavenExplicitProfiles profiles) {
    myProjectsTree.setExplicitProfiles(profiles);
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   */
  @ApiStatus.Internal
  public Promise<Void> scheduleUpdateAll(MavenImportSpec spec) {
    if (MavenUtil.isLinearImportEnabled()) {
      return MavenImportingManager.getInstance(myProject).scheduleImportAll(spec).getFinishPromise().then(it -> null);
    }

    return scheduleUpdateAllSuspendable(spec);
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   */
  private Promise<Void> scheduleUpdateAllSuspendable(MavenImportSpec spec) {
    final AsyncPromise<Void> promise = new AsyncPromise<>();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      updateAllMavenProjectsSync(spec, promise);
    }
    else {
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        updateAllMavenProjectsSync(spec, promise);
      });
    }

    return promise;
  }

  private void updateAllMavenProjectsSync(MavenImportSpec spec, AsyncPromise<Void> promise) {
    try {
      updateAllMavenProjectsSync(spec);
      promise.setResult(null);
    }
    catch (Exception e) {
      promise.setError(e);
    }
  }

  private void scheduleUpdateAllProjects(MavenImportSpec spec) {
    scheduleUpdateProjects(List.of(), spec);
  }

  @ApiStatus.Internal
  public Promise<Void> scheduleUpdate(
    @NotNull List<VirtualFile> filesToUpdate,
    @NotNull List<VirtualFile> filesToDelete,
    MavenImportSpec spec
  ) {

    if (MavenUtil.isLinearImportEnabled()) {
      return MavenImportingManager.getInstance(myProject).scheduleUpdate(filesToUpdate, filesToDelete, spec).getFinishPromise()
        .then(it -> null);
    }

    return scheduleUpdateSuspendable(spec, filesToUpdate, filesToDelete);
  }

  private Promise<Void> scheduleUpdateSuspendable(MavenImportSpec spec,
                                                  List<VirtualFile> filesToUpdate,
                                                  List<VirtualFile> filesToDelete) {
    final AsyncPromise<Void> promise = new AsyncPromise<>();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
        updateMavenProjectsSync(spec, filesToUpdate, filesToDelete, promise);
      }
      else {
        MavenLog.LOG.warn("updateAllMavenProjectsSync skipped in write action");
      }
    }
    else {
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        updateMavenProjectsSync(spec, filesToUpdate, filesToDelete, promise);
      });
    }

    return promise;
  }

  private void updateMavenProjectsSync(MavenImportSpec spec,
                                       List<VirtualFile> filesToUpdate,
                                       List<VirtualFile> filesToDelete,
                                       AsyncPromise<Void> promise) {
    try {
      updateMavenProjectsSync(spec, filesToUpdate, filesToDelete);
      promise.setResult(null);
    }
    catch (Exception e) {
      promise.setError(e);
    }
  }

  @ApiStatus.Internal
  public void forceUpdateProjects() {
    scheduleUpdateProjects(List.of(), MavenImportSpec.EXPLICIT_IMPORT);
  }

  public AsyncPromise<Void> forceUpdateProjects(@NotNull Collection<MavenProject> projects) {
    return (AsyncPromise<Void>)scheduleUpdateProjects(projects, MavenImportSpec.EXPLICIT_IMPORT);
  }

  public void forceUpdateAllProjectsOrFindAllAvailablePomFiles() {
    forceUpdateAllProjectsOrFindAllAvailablePomFiles(MavenImportSpec.EXPLICIT_IMPORT);
  }

  public void forceUpdateAllProjectsOrFindAllAvailablePomFiles(MavenImportSpec spec) {

    if (!isMavenizedProject()) {
      addManagedFiles(collectAllAvailablePomFiles());
    }
    if (MavenUtil.isLinearImportEnabled()) {
      MavenLog.LOG.warn("forceUpdateAllProjectsOrFindAllAvailablePomFiles: Linear Import is enabled");
      MavenImportingManager.getInstance(myProject)
        .openProjectAndImport(new FilesList(myProjectsTree.getExistingManagedFiles()), getImportingSettings(), getGeneralSettings(), spec);
      return;
    }
    MavenLog.LOG.warn("forceUpdateAllProjectsOrFindAllAvailablePomFiles: Linear Import is disabled");
    scheduleUpdateProjects(List.of(), spec);
  }

  @ApiStatus.Internal
  public Promise<Void> scheduleUpdateProjects(
    @NotNull final Collection<MavenProject> projects,
    final MavenImportSpec spec
  ) {
    if (MavenUtil.isLinearImportEnabled()) {
      MavenLog.LOG.warn("scheduleUpdateProjects: Linear Import is enabled");
      return MavenImportingManager.getInstance(myProject)
        .openProjectAndImport(new FilesList(ContainerUtil.map(projects, MavenProject::getFile)), getImportingSettings(),
                              getGeneralSettings(), spec).getFinishPromise().then(it -> null);
    }
    MavenLog.LOG.warn("scheduleUpdateProjects: Linear Import is disabled");
    MavenDistributionsCache.getInstance(myProject).cleanCaches();
    MavenWslCache.getInstance().clearCache();
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> {
      if (projects.isEmpty()) {
        scheduleUpdateAll(spec).processed(promise);
      }
      else {
        scheduleUpdate(MavenUtil.collectFiles(projects), List.of(), spec).processed(promise);
      }
    });
    return promise;
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   * @deprecated  Use {@link #updateAllMavenProjectsSync(MavenImportSpec)}}
   */
  // used in third-party plugins
  @Deprecated
  public Promise<List<Module>> scheduleImportAndResolve() {
    var promise = new AsyncPromise<List<Module>>();
    var modules = updateAllMavenProjectsSync(MavenImportSpec.EXPLICIT_IMPORT);
    promise.setResult(modules);
    return promise;
  }

  public void showServerException(Throwable e) {
    getSyncConsole().addException(e, myProgressListener);
  }

  public void terminateImport(int exitCode) {
    getSyncConsole().terminated(exitCode);
  }

  @ApiStatus.Internal
  public Promise<?> waitForImportCompletion() {
    if (MavenUtil.isLinearImportEnabled()) return MavenImportingManager.getInstance(myProject).getImportFinishPromise();

    waitForPluginResolution();

    AsyncPromise<?> promise = new AsyncPromise<>();
    promise.setResult(null);
    return promise;
  }

  // used in third-party plugins
  public void scheduleFoldersResolveForAllProjects() {
    MavenProjectsManagerUtilKt.scheduleFoldersResolveForAllProjects(myProject);
  }


  private void scheduleImportChangedProjects() {
    runWhenFullyOpen(() -> myImportingQueue.queue(new Update(this) {
      @Override
      public void run() {
        importMavenProjectsSync();
        fireProjectImportCompleted();
      }
    }));
  }

  protected abstract void importMavenProjectsSync();

  @TestOnly
  public boolean hasScheduledImportsInTests() {
    checkNoLegacyImportInNewTests();
    if (!isInitialized()) return false;
    return !myImportingQueue.isEmpty();
  }

  @TestOnly
  public void performScheduledImportInTests() {
    if (!isInitialized()) return;
    runWhenFullyOpen(() -> myImportingQueue.flush());
  }

  private static void checkNoLegacyImportInNewTests() {
    if (ApplicationManager.getApplication().isUnitTestMode() && MavenUtil.isLinearImportEnabled()) {
      throw new IllegalStateException("Do not call this API in tests");
    }
  }

  @ApiStatus.Internal
  public void runWhenFullyOpen(final Runnable runnable) {
    if (!isInitialized()) return; // may be called from scheduleImport after project started closing and before it is closed.

    final Ref<Runnable> wrapper = new Ref<>();
    wrapper.set((DumbAwareRunnable)() -> {
      if (!StartupManagerEx.getInstanceEx(myProject).postStartupActivityPassed()) {
        myInitializationAlarm.addRequest(wrapper.get(), 1000);
        return;
      }
      runnable.run();
    });
    MavenUtil.runWhenInitialized(myProject, wrapper.get());
  }

  public void waitForReadingCompletion() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }
  }

  /**
   * @deprecated This method returns immediately. Kept for compatibility reasons.
   */
  @Deprecated(forRemoval = true)
  public void waitForPostImportTasksCompletion() {
  }

  public void updateProjectTargetFolders() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;

      MavenProjectImporter.tryUpdateTargetFolders(myProject);
      VirtualFileManager.getInstance().asyncRefresh();
    });
  }

  /**
   * @deprecated Use {@link #updateAllMavenProjectsSync(MavenImportSpec)}}
   */
  @Deprecated
  // used in third-party plugins
  public List<Module> importProjects() {
    return updateAllMavenProjectsSync(MavenImportSpec.IMPLICIT_IMPORT);
  }

  @ApiStatus.Internal
  public Map<VirtualFile, Module> getFileToModuleMapping(MavenModelsProvider modelsProvider) {
    Map<VirtualFile, Module> result = new HashMap<>();
    for (Module each : modelsProvider.getModules()) {
      VirtualFile f = findPomFile(each, modelsProvider);
      if (f != null) result.put(f, each);
    }
    return result;
  }

  @ApiStatus.Internal
  public List<VirtualFile> collectAllAvailablePomFiles() {
    List<VirtualFile> result = new ArrayList<>(getFileToModuleMapping(new MavenDefaultModelsProvider(myProject)).keySet());
    MavenUtil.streamPomFiles(myProject, myProject.getBaseDir()).forEach(result::add);
    return result;
  }


  /**
   * @deprecated use addManagerListener(Listener, Disposable) instead
   */
  @Deprecated
  public void addManagerListener(Listener listener) {
    myManagerListeners.add(listener);
  }

  public void addManagerListener(Listener listener, @NotNull Disposable parentDisposable) {
    myManagerListeners.add(listener);
    Disposer.register(parentDisposable, () -> myManagerListeners.remove(listener));
  }

  public void addProjectsTreeListener(MavenProjectsTree.Listener listener) {
    myProjectsTreeDispatcher.addListener(listener, this);
  }

  public void addProjectsTreeListener(@NotNull MavenProjectsTree.Listener listener, @NotNull Disposable parentDisposable) {
    myProjectsTreeDispatcher.addListener(listener, parentDisposable);
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

  protected void fireImportAndResolveScheduled() {
    for (Listener each : myManagerListeners) {
      each.importAndResolveScheduled();
    }
  }


  void fireProjectImportCompleted() {
    for (Listener each : myManagerListeners) {
      each.projectImportCompleted();
    }
  }

  public void setForceUpdateSnapshots(boolean forceUpdateSnapshots) {
    this.forceUpdateSnapshots = forceUpdateSnapshots;
  }

  public boolean getForceUpdateSnapshots() {
    return forceUpdateSnapshots;
  }

  public interface Listener {
    default void activated() {
    }

    default void importAndResolveScheduled() {
    }

    default void projectImportCompleted() {
    }
  }
}
