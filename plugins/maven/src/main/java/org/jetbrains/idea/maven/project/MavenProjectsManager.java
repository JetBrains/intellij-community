// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.SyncViewManager;
import com.intellij.configurationStore.SettingsSavingComponentJavaAdapter;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.autoimport.ExternalSystemProjectsWatcherImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.CacheForCompilerErrorMessages;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenModelUtil;
import org.jetbrains.idea.maven.importing.MavenPomPathModuleService;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.importing.tree.MavenProjectTreeImporter;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader.DownloadResult;
import org.jetbrains.idea.maven.project.importing.FilesList;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@State(name = "MavenProjectsManager")
public final class MavenProjectsManager extends MavenSimpleProjectComponent
  implements PersistentStateComponent<MavenProjectsManagerState>, SettingsSavingComponentJavaAdapter, Disposable {
  private static final int IMPORT_DELAY = 1000;

  private final ReentrantLock initLock = new ReentrantLock();
  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

  private final Alarm myInitializationAlarm;

  private final MavenEmbeddersManager myEmbeddersManager;

  private MavenProjectsTree myProjectsTree;
  private MavenProjectResolver myMavenProjectResolver;
  private MavenProjectsManagerWatcher myWatcher;

  private MavenProjectsProcessor myReadingProcessor;
  private MavenProjectsProcessor myResolvingProcessor;
  private MavenProjectsProcessor myPluginsResolvingProcessor;
  private MavenProjectsProcessor myFoldersResolvingProcessor;
  private MavenProjectsProcessor myArtifactsDownloadingProcessor;
  private MavenProjectsProcessor myPostProcessor;

  private MavenMergingUpdateQueue myImportingQueue;
  private final Object myImportingDataLock = new Object();
  private final Map<MavenProject, MavenProjectChanges> myProjectsToImport = new LinkedHashMap<>();
  private final Set<MavenProject> myProjectsToResolve = new LinkedHashSet<>();

  private boolean myImportModuleGroupsRequired = false;

  private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher =
    EventDispatcher.create(MavenProjectsTree.Listener.class);
  private final List<Listener> myManagerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ModificationTracker myModificationTracker;
  private BuildProgressListener myProgressListener;

  private MavenWorkspaceSettings myWorkspaceSettings;

  private volatile MavenSyncConsole mySyncConsole;
  private final MavenMergingUpdateQueue mySaveQueue;
  private static final int SAVE_DELAY = 1000;
  private Module myDummyModule;

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

  @TestOnly
  public void setProgressListener(SyncViewManager testViewManager) {
    myProgressListener = testViewManager;
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
      scheduleUpdateAllProjects(false);
    }
  }

  @Override
  public void dispose() {
    mySyncConsole = null;
    myManagerListeners.clear();
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
    if (!isNormalProject()) {
      return;
    }

    Runnable runnable = () -> {
      boolean wasMavenized = !myState.originalFiles.isEmpty();
      if (!wasMavenized) {
        return;
      }
      initMavenized();
    };

    StartupManager startupManager = StartupManager.getInstance(myProject);
    if (startupManager.postStartupActivityPassed()) {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
    else {
      startupManager.registerStartupActivity(runnable);
    }
  }

  private void initMavenized() {
    doInit(false);
  }

  private void initNew(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myState.originalFiles = MavenUtil.collectPaths(files);
    MavenWorkspaceSettings workspaceSettings = getWorkspaceSettings();
    workspaceSettings.setEnabledProfiles(explicitProfiles.getEnabledProfiles());
    workspaceSettings.setDisabledProfiles(explicitProfiles.getDisabledProfiles());
    doInit(true);
  }

  @TestOnly
  public void initForTests() {
    doInit(false);
  }

  private void doInit(final boolean isNew) {
    initLock.lock();
    try {
      if (isInitialized.getAndSet(true)) {
        return;
      }
      initPreloadMavenServices();
      initProjectsTree(!isNew);
      initWorkers();
      listenForSettingsChanges();
      listenForProjectsTreeChanges();
      registerSyncConsoleListener();
      updateTabTitles();


      MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          fireActivated();
          listenForExternalChanges();
        }
        if (!Registry.is("maven.new.import")) {
          scheduleUpdateAllProjects(isNew);
        }
      });
    }
    finally {
      initLock.unlock();
    }
  }

  private void initPreloadMavenServices() {
    // init maven tool window
    MavenProjectsNavigator.getInstance(myProject);
    // init indices manager
    MavenIndicesManager.getInstance(myProject);
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

  public synchronized MavenSyncConsole getSyncConsole() {
    if (mySyncConsole == null) {
      mySyncConsole = new MavenSyncConsole(myProject);
    }
    return mySyncConsole;
  }

  private void initProjectsTree(boolean tryToLoadExisting) {
    if (tryToLoadExisting) {
      Path file = getProjectsTreeFile();
      try {
        if (PathKt.exists(file)) {
          myProjectsTree = MavenProjectsTree.read(myProject, file);
        }
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }

    if (myProjectsTree == null) myProjectsTree = new MavenProjectsTree(myProject);
    myMavenProjectResolver = new MavenProjectResolver(myProjectsTree);
    applyStateToTree(myProjectsTree, this);
    myProjectsTree.addListener(myProjectsTreeDispatcher.getMulticaster(), this);
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
    myReadingProcessor = new MavenProjectsProcessor(myProject, MavenProjectBundle.message("maven.reading"), false, myEmbeddersManager);
    myResolvingProcessor = new MavenProjectsProcessor(myProject, MavenProjectBundle.message("maven.resolving"), true, myEmbeddersManager);
    myPluginsResolvingProcessor =
      new MavenProjectsProcessor(myProject, MavenProjectBundle.message("maven.downloading.plugins"), true, myEmbeddersManager);
    myFoldersResolvingProcessor =
      new MavenProjectsProcessor(myProject, MavenProjectBundle.message("maven.updating.folders"), true, myEmbeddersManager);
    myArtifactsDownloadingProcessor =
      new MavenProjectsProcessor(myProject, MavenProjectBundle.message("maven.downloading"), true, myEmbeddersManager);
    myPostProcessor = new MavenProjectsProcessor(myProject, MavenProjectBundle.message("maven.post.processing"), true, myEmbeddersManager);

    myWatcher = new MavenProjectsManagerWatcher(myProject, myProjectsTree, getGeneralSettings(), myReadingProcessor);

    myImportingQueue =
      new MavenMergingUpdateQueue(getClass().getName() + ": Importing queue", IMPORT_DELAY, !MavenUtil.isMavenUnitTestModeEnabled(), this);

    myImportingQueue.makeUserAware(myProject);
    myImportingQueue.makeModalAware(myProject);
  }

  private void listenForSettingsChanges() {
    getImportingSettings().addListener(new MavenImportingSettings.Listener() {
      @Override
      public void createModuleGroupsChanged() {
        scheduleImportSettings(true);
      }

      @Override
      public void createModuleForAggregatorsChanged() {
        scheduleImportSettings();
      }
    });
  }

  private void registerSyncConsoleListener() {
    myProjectsTreeDispatcher.addListener(new MavenProjectsTree.Listener() {
      @Override
      public void pluginsResolved(@NotNull MavenProject project) {
        getSyncConsole().getListener(MavenServerProgressIndicator.ResolveType.PLUGIN).finish();
      }

      @Override
      public void artifactsDownloaded(@NotNull MavenProject project) {
        getSyncConsole().getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY).finish();
      }
    });
  }

  private void listenForProjectsTreeChanges() {
    myProjectsTree.addListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsIgnoredStateChanged(@NotNull List<MavenProject> ignored,
                                              @NotNull List<MavenProject> unignored,
                                              boolean fromImport) {
        if (!fromImport) scheduleImport();
      }

      @Override
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
        myEmbeddersManager.clearCaches();

        unscheduleAllTasks(deleted);

        List<MavenProject> updatedProjects = MavenUtil.collectFirsts(updated);

        // import only updated projects and dependents of them (we need to update faced-deps, packaging etc);
        List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<>(updated);

        for (MavenProject eachDependent : myProjectsTree.getDependentProjects(updatedProjects)) {
          toImport.add(Pair.create(eachDependent, MavenProjectChanges.DEPENDENCIES));
        }

        // resolve updated, theirs dependents, and dependents of deleted
        Set<MavenProject> toResolve = new HashSet<>(updatedProjects);
        toResolve.addAll(myProjectsTree.getDependentProjects(ContainerUtil.concat(updatedProjects, deleted)));

        // do not try to resolve projects with syntactic errors
        Iterator<MavenProject> it = toResolve.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          if (each.hasReadingProblems()) {
            getSyncConsole().notifyReadingProblems(each.getFile());
            it.remove();
          }
        }

        if (haveChanges(toImport) || !deleted.isEmpty()) {
          scheduleForNextImport(toImport);
        }

        if (!deleted.isEmpty() && !hasScheduledProjects()) {
          MavenProject project = ObjectUtils.chooseNotNull(ContainerUtil.getFirstItem(toResolve),
                                                           ContainerUtil.getFirstItem(getNonIgnoredProjects()));
          if (project != null) {
            scheduleForNextImport(Pair.create(project, MavenProjectChanges.ALL));
            scheduleForNextResolve(Collections.singletonList(project));
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
      public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  @Nullable NativeMavenProjectHolder nativeMavenProject) {
        if (nativeMavenProject != null) {
          if (shouldScheduleProject(projectWithChanges)) {
            scheduleForNextImport(projectWithChanges);

            MavenImportingSettings importingSettings;

            importingSettings = ReadAction.compute(() -> myProject.isDisposed() ? null : getImportingSettings());
            if (importingSettings == null) return;

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
      public void foldersResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        if (shouldScheduleProject(projectWithChanges)) {
          scheduleForNextImport(projectWithChanges);
        }
      }

      private boolean shouldScheduleProject(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        return !projectWithChanges.first.hasReadingProblems() && projectWithChanges.second.hasChanges();
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
      if (!isInitialized.getAndSet(false)) return;

      Disposer.dispose(myImportingQueue);

      myWatcher.stop();

      myReadingProcessor.stop();
      myResolvingProcessor.stop();
      myPluginsResolvingProcessor.stop();
      myFoldersResolvingProcessor.stop();
      myArtifactsDownloadingProcessor.stop();
      myPostProcessor.stop();
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
    return ReadAction.compute(() -> !m.isDisposed() && ExternalSystemModulePropertyManager.getInstance(m).isMavenized());
  }

  @TestOnly
  public void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles profiles) {
    myWatcher.resetManagedFilesAndProfilesInTests(files, profiles);
  }


  public void addManagedFilesWithProfiles(final List<VirtualFile> files, MavenExplicitProfiles profiles, Module dummyModuleToDelete) {
    myDummyModule = dummyModuleToDelete;
    if (!isInitialized()) {
      initNew(files, profiles);
    }
    else {
      myWatcher.addManagedFilesWithProfiles(files, profiles);
    }
  }

  public void addManagedFiles(@NotNull List<VirtualFile> files) {
    addManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE, null);
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
    if (mavenProject == null && MavenModelUtil.isMainOrTestSubmodule(moduleName)) {
      Module parentModule = ModuleManager.getInstance(myProject).findModuleByName(moduleName.substring(0, moduleName.length() - 5));
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
  private VirtualFile findPomFile(@NotNull Module module, @NotNull MavenModelsProvider modelsProvider) {
    String pomFileUrl = MavenPomPathModuleService.getInstance(module).getPomFileUrl();
    if (pomFileUrl != null) {
      return VirtualFileManager.getInstance().findFileByUrl(pomFileUrl);
    }
    for (VirtualFile root : modelsProvider.getContentRoots(module)) {
      List<VirtualFile> pomFiles = MavenUtil.streamPomFiles(module.getProject(), root).collect(Collectors.toList());
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
  public void setProjectsTree(MavenProjectsTree newTree) {
    myProjectsTree = newTree;
  }

  @ApiStatus.Internal
  @Nullable
  public MavenProjectsTree getProjectsTree() {
    return myProjectsTree;
  }

  private void scheduleUpdateAllProjects(boolean forceImportAndResolve) {
    doScheduleUpdateProjects(null, false, forceImportAndResolve);
  }

  @ApiStatus.Internal
  public AsyncPromise<Void> forceUpdateProjects() {
    return (AsyncPromise<Void>)doScheduleUpdateProjects(null, true, true);
  }

  public AsyncPromise<Void> forceUpdateProjects(@NotNull Collection<MavenProject> projects) {
    return (AsyncPromise<Void>)doScheduleUpdateProjects(projects, true, true);
  }

  public void forceUpdateAllProjectsOrFindAllAvailablePomFiles() {
    if (Registry.is("maven.new.import")) {
      MavenImportingManager.getInstance(myProject)
        .openProjectAndImport(new FilesList(collectAllAvailablePomFiles()), getImportingSettings(), getGeneralSettings());
      return;
    }
    if (!isMavenizedProject()) {
      addManagedFiles(collectAllAvailablePomFiles());
    }
    doScheduleUpdateProjects(null, true, true);
  }

  private Promise<Void> doScheduleUpdateProjects(final Collection<MavenProject> projects,
                                                      final boolean forceUpdate,
                                                      final boolean forceImportAndResolve) {
    if (Registry.is("maven.new.import")){
      return MavenImportingManager.getInstance(myProject)
        .openProjectAndImport(new FilesList(ContainerUtil.map(projects, MavenProject::getFile)), getImportingSettings(), getGeneralSettings()).then(it -> null);
    }
    MavenDistributionsCache.getInstance(myProject).cleanCaches();
    MavenWslCache.getInstance().clearCache();
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> {
      if (projects == null) {
        myWatcher.scheduleUpdateAll(forceUpdate, forceImportAndResolve).processed(promise);
      }
      else {
        myWatcher.scheduleUpdate(MavenUtil.collectFiles(projects),
                                 Collections.emptyList(),
                                 forceUpdate,
                                 forceImportAndResolve).processed(promise);
      }
    });
    return promise;
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   */
  public Promise<List<Module>> scheduleImportAndResolve() {
    MavenSyncConsole console = getSyncConsole();
    console.startImport(myProgressListener);
    fireImportAndResolveScheduled();
    AsyncPromise<List<Module>> promise = scheduleResolve();
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
    if(Registry.is("maven.new.import")) return MavenImportingManager.getInstance(myProject).getImportFinishPromise();

    AsyncPromise<?> promise = new AsyncPromise<>();
    MavenUtil.runInBackground(myProject, SyncBundle.message("maven.sync.waiting.for.completion"), false, indicator -> {
      if (myReadingProcessor != null) {
        myReadingProcessor.waitForCompletion();
      }
      if (myArtifactsDownloadingProcessor != null) {
        myArtifactsDownloadingProcessor.waitForCompletion();
      }
      if (myFoldersResolvingProcessor != null) {
        myFoldersResolvingProcessor.waitForCompletion();
      }
      if (myPluginsResolvingProcessor != null) {
        myPluginsResolvingProcessor.waitForCompletion();
      }
      if (myResolvingProcessor != null) {
        myResolvingProcessor.waitForCompletion();
      }
      if (myPostProcessor != null) {
        myPostProcessor.waitForCompletion();
      }
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        MavenProgressIndicator.MavenProgressTracker mavenProgressTracker =
          myProject.getServiceIfCreated(MavenProgressIndicator.MavenProgressTracker.class);
        if (mavenProgressTracker != null) {
          mavenProgressTracker.waitForProgressCompletion();
        }
        promise.setResult(null);
      });
    });
    return promise;
  }

  private AsyncPromise<List<Module>> scheduleResolve() {
    final AsyncPromise<List<Module>> result = new AsyncPromise<>();
    runWhenFullyOpen(() -> {
      LinkedHashSet<MavenProject> toResolve;
      synchronized (myImportingDataLock) {
        toResolve = new LinkedHashSet<>(myProjectsToResolve);
        myProjectsToResolve.clear();
      }
      if (toResolve.isEmpty()) {
        result.setResult(Collections.emptyList());
        fireProjectImportCompleted();
        return;
      }

      final ResolveContext context = new ResolveContext();
      Runnable onCompletion = () -> {
        if (hasScheduledProjects()) {
          scheduleImport().processed(result);
        }
        else {
          result.setResult(Collections.emptyList());
          fireProjectImportCompleted();
        }
      };

      final boolean useSinglePomResolver = Boolean.getBoolean("idea.maven.use.single.pom.resolver");
      if (useSinglePomResolver) {
        Iterator<MavenProject> it = toResolve.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          myResolvingProcessor.scheduleTask(new MavenProjectsProcessorResolvingTask(
            Collections.singleton(each), myProjectsTree, getGeneralSettings(), it.hasNext() ? null : onCompletion, context));
        }
      }
      else {
        myResolvingProcessor.scheduleTask(
          new MavenProjectsProcessorResolvingTask(toResolve, myProjectsTree, getGeneralSettings(), onCompletion, context));
      }
    });
    return result;
  }

  public void evaluateEffectivePom(@NotNull final MavenProject mavenProject, @NotNull final NullableConsumer<? super String> consumer) {
    runWhenFullyOpen(() -> myResolvingProcessor.scheduleTask(new MavenProjectsProcessorTask() {
      @Override
      public void perform(Project project,
                          MavenEmbeddersManager embeddersManager,
                          MavenConsole console,
                          MavenProgressIndicator indicator)
        throws MavenProcessCanceledException {

        indicator.setText(MavenProjectBundle.message("maven.project.importing.evaluating.effective.pom"));

        myMavenProjectResolver.executeWithEmbedder(mavenProject,
                                                   getEmbeddersManager(),
                                                   MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE,
                                                   console,
                                                   indicator,
                                                   new MavenProjectResolver.EmbedderTask() {
                                                     @Override
                                                     public void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
                                                       try {
                                                         MavenExplicitProfiles profiles = mavenProject.getActivatedProfilesIds();
                                                         VirtualFile virtualFile = mavenProject.getFile();
                                                         File projectFile = MavenWslUtil.resolveWslAware(myProject,
                                                                                                         () -> new File(
                                                                                                           virtualFile.getPath()),
                                                                                                         wsl -> MavenWslUtil.getWslFile(wsl,
                                                                                                                                        new File(
                                                                                                                                          virtualFile.getPath())));
                                                         String res =
                                                           embedder
                                                             .evaluateEffectivePom(projectFile, profiles.getEnabledProfiles(),
                                                                                   profiles.getDisabledProfiles());
                                                         consumer.consume(res);
                                                       }
                                                       catch (UnsupportedOperationException e) {
                                                         e.printStackTrace();
                                                         consumer.consume(null); // null means UnsupportedOperationException
                                                       }
                                                     }
                                                   });
      }
    }));
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
    runWhenFullyOpen(() -> {
      Iterator<MavenProject> it = projects.iterator();
      while (it.hasNext()) {
        MavenProject each = it.next();
        Runnable onCompletion = it.hasNext() ? null : () -> {
          if (hasScheduledProjects()) scheduleImport();
        };
        myFoldersResolvingProcessor.scheduleTask(
          new MavenProjectsProcessorFoldersResolvingTask(each, getImportingSettings(), myProjectsTree, onCompletion));
      }
    });
  }

  public void scheduleFoldersResolveForAllProjects() {
    scheduleFoldersResolve(getProjects());
  }

  private void schedulePluginsResolve(final MavenProject project, final NativeMavenProjectHolder nativeMavenProject) {
    runWhenFullyOpen(() -> myPluginsResolvingProcessor
      .scheduleTask(new MavenProjectsProcessorPluginsResolvingTask(project, nativeMavenProject, myProjectsTree)));
  }

  public void scheduleArtifactsDownloading(final Collection<MavenProject> projects,
                                           @Nullable final Collection<MavenArtifact> artifacts,
                                           final boolean sources, final boolean docs,
                                           @Nullable final AsyncPromise<DownloadResult> result) {
    if (!sources && !docs) return;

    runWhenFullyOpen(() -> myArtifactsDownloadingProcessor
      .scheduleTask(
        new MavenProjectsProcessorArtifactsDownloadingTask(projects, artifacts, myMavenProjectResolver, sources, docs, result)));
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

  // TODO merge [result] promises (now, promise will be lost after merge of import requests)
  private Promise<List<Module>> scheduleImport() {
    final AsyncPromise<List<Module>> result = new AsyncPromise<>();
    runWhenFullyOpen(() -> myImportingQueue.queue(new Update(this) {
      @Override
      public void run() {
        result.setResult(importProjects());
        fireProjectImportCompleted();
      }
    }));
    return result;
  }

  @TestOnly
  public void scheduleImportInTests(List<VirtualFile> projectFiles) {
    List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<>();
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
    runWhenFullyOpen(() -> myImportingQueue.flush());
  }

  private void runWhenFullyOpen(final Runnable runnable) {
    if (!isInitialized()) return; // may be called from scheduleImport after project started closing and before it is closed.

    if (isNoBackgroundMode()) {
      runnable.run();
      return;
    }

    final Ref<Runnable> wrapper = new Ref<>();
    wrapper.set(() -> {
      if (!StartupManagerEx.getInstanceEx(myProject).postStartupActivityPassed()) {
        myInitializationAlarm.addRequest(wrapper.get(), 1000);
        return;
      }
      runnable.run();
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
    if (ApplicationManager.getApplication().isDispatchThread()) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    myReadingProcessor.waitForCompletion();
    if (processor != null) processor.waitForCompletion();
  }

  public void updateProjectTargetFolders() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;

      MavenFoldersImporter.updateProjectFolders(myProject, true);
      VirtualFileManager.getInstance().asyncRefresh(null);
    });
  }

  public List<Module> importProjects() {
    return importProjects(ProjectDataManager.getInstance().createModifiableModelsProvider(myProject));
  }


  public List<Module> importProjects(final IdeModifiableModelsProvider modelsProvider) {
    final Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges;
    final boolean importModuleGroupsRequired;
    synchronized (myImportingDataLock) {
      projectsToImportWithChanges = Collections.unmodifiableMap(new LinkedHashMap<>(myProjectsToImport));
      myProjectsToImport.clear();
      importModuleGroupsRequired = myImportModuleGroupsRequired;
      myImportModuleGroupsRequired = false;
    }

    final Ref<MavenProjectImporter> importer = new Ref<>();
    final Ref<List<MavenProjectsProcessorTask>> postTasks = new Ref<>();

    final Runnable r = () -> {
      MavenProjectImporter projectImporter = null;
      if (MavenProjectImporter.isImportToTreeStructureEnabled()) {
        projectImporter = new MavenProjectTreeImporter(
          myProject, myProjectsTree, projectsToImportWithChanges, modelsProvider, getImportingSettings()
        );
      } else
      projectImporter = MavenProjectImporter.createImporter(myProject,
                                                                                 myProjectsTree,
                                                                                 getFileToModuleMapping(new MavenModelsProvider() {
                                                                                   @Override
                                                                                   public Module[] getModules() {
                                                                                     return ArrayUtil.remove(modelsProvider.getModules(),
                                                                                                             myDummyModule);
                                                                                   }

                                                                                   @Override
                                                                                   public VirtualFile[] getContentRoots(Module module) {
                                                                                     return modelsProvider.getContentRoots(module);
                                                                                   }
                                                                                 }),
                                                                                 projectsToImportWithChanges,
                                                                                 importModuleGroupsRequired,
                                                                                 modelsProvider,
                                                                                 getImportingSettings(),
                                                                                 myDummyModule);
      importer.set(projectImporter);
      postTasks.set(projectImporter.importProject());
    };

    // called from wizard or ui
    if (ApplicationManager.getApplication().isDispatchThread()) {
      r.run();
    }
    else {
      MavenUtil.runInBackground(myProject, MavenProjectBundle.message("maven.project.importing"), false, new MavenTask() {
        @Override
        public void run(MavenProgressIndicator indicator) {
          r.run();
        }
      }).waitFor();
    }


    VirtualFileManager fm = VirtualFileManager.getInstance();
    if (isNoBackgroundMode() && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
      ApplicationManager.getApplication().invokeAndWait(() -> fm.syncRefresh());
    }
    else {
      fm.asyncRefresh(null);
    }

    if (postTasks.get() != null /*may be null if importing is cancelled*/) {
      schedulePostImportTasks(postTasks.get());
    }

    // do not block user too often
    myImportingQueue.restartTimer();

    MavenProjectImporter projectImporter = importer.get();
    List<Module> createdModules = projectImporter == null ? Collections.emptyList() : projectImporter.getCreatedModules();
    if (!projectsToImportWithChanges.isEmpty()) {
      myProject.getMessageBus().syncPublisher(MavenImportListener.TOPIC)
        .importFinished(projectsToImportWithChanges.keySet(), createdModules);
    }
    return createdModules;
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

  private void fireProjectImportCompleted() {
    for (Listener each : myManagerListeners) {
      each.projectImportCompleted();
    }
  }

  public interface Listener {
    default void activated() {
    }

    default void projectsScheduled() {
    }

    default void importAndResolveScheduled() {
    }

    default void projectImportCompleted() {
    }
  }

  public static class ExternalWatcherContributor implements ExternalSystemProjectsWatcherImpl.Contributor {

    @Override
    public void markDirtyAllExternalProjects(@NotNull Project project) {
      runWhenFullyOpen(project, (manager) -> manager.doScheduleUpdateProjects(null, true, false));
    }

    @Override
    public void markDirty(@NotNull Module module) {
      runWhenFullyOpen(module.getProject(), (manager) -> {
        MavenProject mavenProject = manager.findProject(module);
        if (mavenProject != null) {
          manager.doScheduleUpdateProjects(Collections.singletonList(mavenProject), true, false);
        }
      });
    }

    private static void runWhenFullyOpen(@NotNull Project project, @NotNull Consumer<MavenProjectsManager> consumer) {
      MavenProjectsManager manager = getInstance(project);
      manager.runWhenFullyOpen(() -> consumer.accept(manager));
    }
  }
}
