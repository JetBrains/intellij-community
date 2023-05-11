// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.SyncViewManager;
import com.intellij.configurationStore.SettingsSavingComponentJavaAdapter;
import com.intellij.ide.impl.ProjectUtilKt;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.autoimport.ExternalSystemProjectsWatcherImpl;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemStatUtilKt;
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector;
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
import com.intellij.util.NullableConsumer;
import com.intellij.util.ObjectUtils;
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
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.CacheForCompilerErrorMessages;
import org.jetbrains.idea.maven.importing.MavenImportStats;
import org.jetbrains.idea.maven.importing.MavenImportUtil;
import org.jetbrains.idea.maven.importing.MavenPomPathModuleService;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader.DownloadResult;
import org.jetbrains.idea.maven.project.importing.FilesList;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.jetbrains.idea.maven.project.importing.MavenProjectManagerListenerToBusBridge;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@State(name = "MavenProjectsManager")
public class MavenProjectsManager extends MavenSimpleProjectComponent
  implements PersistentStateComponent<MavenProjectsManagerState>, SettingsSavingComponentJavaAdapter, Disposable {
  private static final int IMPORT_DELAY = 1000;

  private final ReentrantLock initLock = new ReentrantLock();
  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

  private final Alarm myInitializationAlarm;

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
  private Module myPreviewModule;
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
    mySyncConsole = null;
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
    if (!isNormalProject()) {
      return;
    }

    //noinspection deprecation
    ProjectUtilKt.executeOnPooledThread(myProject, () -> {
      boolean wasMavenized = !myState.originalFiles.isEmpty();
      if (!wasMavenized) {
        return;
      }
      initMavenized();
    });
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
      initManagerListenerToBusBridge();
      initBusToManagerListenerBridge();
      initPreloadMavenServices();
      initProjectsTree(!isNew);
      initWorkers();
      listenForSettingsChanges();
      listenForProjectsTreeChanges();
      registerSyncConsoleListener();
      updateTabTitles();

      MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          MavenIndicesManager.getInstance(myProject).scheduleUpdateIndicesList(null);
          fireActivated();
          listenForExternalChanges();
        }
        if (!MavenUtil.isLinearImportEnabled()) {
          scheduleUpdateAllProjects(new MavenImportSpec(false, isNew, false));
        }
      });
    }
    finally {
      initLock.unlock();
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
      public void projectsScheduled() {
        fireProjectScheduled();
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

  public synchronized MavenSyncConsole getSyncConsole() {
    if (mySyncConsole == null) {
      mySyncConsole = new MavenSyncConsole(myProject);
    }
    return mySyncConsole;
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

      @Override
      public void updateAllProjectStructure() {
        scheduleAllProjectImport();
      }
    });
  }

  private void registerSyncConsoleListener() {
    if (MavenUtil.isLinearImportEnabled()) return;
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
    if (MavenUtil.isLinearImportEnabled()) return;
    myProjectsTreeDispatcher.addListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsIgnoredStateChanged(@NotNull List<MavenProject> ignored,
                                              @NotNull List<MavenProject> unignored,
                                              boolean fromImport) {
        if (!fromImport) scheduleImportChangedProjects();
      }

      @Override
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
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
        forceUpdateSnapshots = false;
        if (nativeMavenProject != null) {
          var project = projectWithChanges.first;
          if (shouldScheduleProject(projectWithChanges)) {
            scheduleForNextImport(projectWithChanges);

            MavenImportingSettings importingSettings;

            importingSettings = ReadAction.compute(() -> myProject.isDisposed() ? null : getImportingSettings());
            if (importingSettings == null) return;

            scheduleArtifactsDownloading(Collections.singleton(project),
                                         null,
                                         importingSettings.isDownloadSourcesAutomatically(),
                                         importingSettings.isDownloadDocsAutomatically(),
                                         null);
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

  private void schedulePluginResolution(@NotNull Map<String, Collection<MavenProjectWithHolder>> projectsWithUnresolvedPlugins) {
    runWhenFullyOpen(
      () -> {
        for (var pluginResolutionRequests : projectsWithUnresolvedPlugins.values()) {
          myPluginsResolvingProcessor.scheduleTask(
            new MavenProjectsProcessorPluginsResolvingTask(pluginResolutionRequests, new MavenPluginResolver(getProjectsTree()))
          );
        }
      }
    );
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
    return MavenUtil.isMavenizedModule(m);
  }

  @TestOnly
  public void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles profiles) {
    myWatcher.resetManagedFilesAndProfilesInTests(files, profiles);
  }


  public void addManagedFilesWithProfiles(final List<VirtualFile> files, MavenExplicitProfiles profiles, Module previewModuleToDelete) {
    myPreviewModule = previewModuleToDelete;
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
      initNew(Collections.emptyList(), MavenExplicitProfiles.NONE);
    }
    newTree.addListenersFrom(myProjectsTree);
    myProjectsTree = newTree;
    myWatcher.setProjectsTree(newTree);
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

  private void scheduleUpdateAllProjects(MavenImportSpec spec) {
    doScheduleUpdateProjects(List.of(), spec);
  }

  @ApiStatus.Internal
  public AsyncPromise<Void> forceUpdateProjects() {
    return (AsyncPromise<Void>)doScheduleUpdateProjects(List.of(), MavenImportSpec.EXPLICIT_IMPORT);
  }

  public AsyncPromise<Void> forceUpdateProjects(@NotNull Collection<MavenProject> projects) {
    return (AsyncPromise<Void>)doScheduleUpdateProjects(projects, MavenImportSpec.EXPLICIT_IMPORT);
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
    doScheduleUpdateProjects(List.of(), spec);
  }

  private Promise<Void> doScheduleUpdateProjects(@NotNull final Collection<MavenProject> projects,
                                                 final MavenImportSpec spec) {
    if (MavenUtil.isLinearImportEnabled()) {
      MavenLog.LOG.warn("doScheduleUpdateProjects: Linear Import is enabled");
      return MavenImportingManager.getInstance(myProject)
        .openProjectAndImport(new FilesList(ContainerUtil.map(projects, MavenProject::getFile)), getImportingSettings(),
                              getGeneralSettings(), spec).getFinishPromise().then(it -> null);
    }
    MavenLog.LOG.warn("doScheduleUpdateProjects: Linear Import is disabled");
    MavenDistributionsCache.getInstance(myProject).cleanCaches();
    MavenWslCache.getInstance().clearCache();
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> {
      if (projects.isEmpty()) {
        myWatcher.scheduleUpdateAll(spec).processed(promise);
      }
      else {
        myWatcher.scheduleUpdate(MavenUtil.collectFiles(projects),
                                 Collections.emptyList(),
                                 spec).processed(promise);
      }
    });
    return promise;
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   */

  public Promise<List<Module>> scheduleImportAndResolve() {
    return scheduleImportAndResolve(MavenImportSpec.EXPLICIT_IMPORT);
  }

  public Promise<List<Module>> scheduleImportAndResolve(MavenImportSpec spec) {
    MavenSyncConsole console = getSyncConsole();
    console.startImport(myProgressListener, spec);
    StructuredIdeActivity activity = MavenImportStats.startImportActivity(myProject);
    fireImportAndResolveScheduled(spec);
    AsyncPromise<List<Module>> promise = scheduleResolve();
    promise.onProcessed(m -> {
      completeMavenSyncOnImportCompletion(activity);
    });
    return promise;
  }

  private void completeMavenSyncOnImportCompletion(StructuredIdeActivity activity) {
    waitForImportCompletion().onProcessed(o -> {
      activity.finished();
      MavenResolveResultProblemProcessor.notifyMavenProblems(myProject);
      MavenSyncConsole.finishTransaction(myProject);
    });
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
        myProject.getMessageBus().syncPublisher(MavenImportListener.TOPIC)
          .importFinished(Collections.emptyList(), Collections.emptyList());
        fireProjectImportCompleted();
        return;
      }

      Consumer<MavenProjectResolver.MavenProjectResolutionResult> onCompletion = resolutionResult -> {
        schedulePluginResolution(resolutionResult.projectsWithUnresolvedPlugins());
        if (hasScheduledProjects()) {
          scheduleImportChangedProjects().processed(result);
        }
        else {
          result.setResult(Collections.emptyList());
          myProject.getMessageBus().syncPublisher(MavenImportListener.TOPIC)
            .importFinished(Collections.emptyList(), Collections.emptyList());
          fireProjectImportCompleted();
        }
      };

      myResolvingProcessor.scheduleTask(new MavenProjectsProcessorResolvingTask(toResolve, getGeneralSettings(), myProjectsTree, onCompletion));
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

        MavenEmbeddersManager.EmbedderTask task = new MavenEmbeddersManager.EmbedderTask() {
          @Override
          public void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
            try {
              MavenExplicitProfiles profiles = mavenProject.getActivatedProfilesIds();
              VirtualFile virtualFile = mavenProject.getFile();
              File projectFile = MavenWslUtil.resolveWslAware(
                myProject,
                () -> new File(virtualFile.getPath()),
                wsl -> MavenWslUtil.getWslFile(wsl, new File(virtualFile.getPath()))
              );
              String res = embedder.evaluateEffectivePom(projectFile, profiles.getEnabledProfiles(), profiles.getDisabledProfiles());
              consumer.consume(res);
            }
            catch (UnsupportedOperationException e) {
              MavenLog.LOG.error(e);
              consumer.consume(null); // null means UnsupportedOperationException
            }
          }
        };

        getEmbeddersManager().execute(
          mavenProject,
          myProjectsTree,
          MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE,
          console,
          indicator,
          task);
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

  public Promise<?> scheduleFoldersResolve(@NotNull Collection<MavenProject> projects) {
    if (MavenUtil.isLinearImportEnabled()) {
      return MavenImportingManager.getInstance(myProject).resolveFolders(projects);
    }
    AsyncPromise<Void> result = new AsyncPromise<>();
    runWhenFullyOpen(() -> {
      Runnable onCompletion = () -> {
        result.setResult(null);
        if (hasScheduledProjects()) scheduleImportChangedProjects();
      };
      myFoldersResolvingProcessor.scheduleTask(
        new MavenProjectsProcessorFoldersResolvingTask(projects, getImportingSettings(), myProjectsTree, onCompletion));
    });
    return result;
  }

  public void scheduleFoldersResolveForAllProjects() {
    scheduleFoldersResolve(getProjects());
  }

  public void scheduleArtifactsDownloading(final Collection<MavenProject> projects,
                                           @Nullable final Collection<MavenArtifact> artifacts,
                                           final boolean sources, final boolean docs,
                                           @Nullable final AsyncPromise<DownloadResult> result) {
    if (!sources && !docs) return;

    runWhenFullyOpen(() -> myArtifactsDownloadingProcessor
      .scheduleTask(
        new MavenProjectsProcessorArtifactsDownloadingTask(projects, myProjectsTree, artifacts, sources, docs, result)));
  }

  private void scheduleImportSettings() {
    scheduleImportSettings(false);
  }

  private void scheduleImportSettings(boolean importModuleGroupsRequired) {
    synchronized (myImportingDataLock) {
      myImportModuleGroupsRequired = importModuleGroupsRequired;
    }
    scheduleImportChangedProjects();
  }

  // TODO merge [result] promises (now, promise will be lost after merge of import requests)
  private Promise<List<Module>> scheduleImportChangedProjects() {
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

  private void scheduleAllProjectImport() {
    runWhenFullyOpen(() -> myImportingQueue.queue(new Update(this) {
      @Override
      public void run() {
        synchronized (myImportingDataLock) {
          for (MavenProject project : getProjectsTree().getProjects()) {
            myProjectsToImport.put(project, MavenProjectChanges.ALL);
          }
        }
        importProjects();
        fireProjectImportCompleted();
      }
    }));
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
    scheduleImportChangedProjects();
  }

  private void scheduleForNextImport(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    scheduleForNextImport(Collections.singletonList(projectWithChanges));
  }

  private void scheduleForNextImport(Collection<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
    synchronized (myImportingDataLock) {
      for (Pair<MavenProject, MavenProjectChanges> each : projectsWithChanges) {
        myProjectsToImport.compute(each.first, (__, previousChanges) ->
          previousChanges == null ? each.second : MavenProjectChangesBuilder.merged(each.second, previousChanges)
        );
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

  private void runWhenFullyOpen(final Runnable runnable) {
    if (!isInitialized()) return; // may be called from scheduleImport after project started closing and before it is closed.

    if (isNoBackgroundMode()) {
      runnable.run();
      return;
    }

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

  private void schedulePostImportTasks(List<MavenProjectsProcessorTask> postTasks) {
    for (MavenProjectsProcessorTask each : postTasks) {
      myPostProcessor.scheduleTask(each);
    }
  }

  private void unscheduleAllTasks(List<MavenProject> projects) {
    for (MavenProject each : projects) {
      synchronized (myImportingDataLock) {
        myProjectsToImport.remove(each);
        myProjectsToResolve.remove(each);
      }
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

      MavenProjectImporter.tryUpdateTargetFolders(myProject);
      VirtualFileManager.getInstance().asyncRefresh();
    });
  }

  public List<Module> importProjects() {
    return importProjects(ProjectDataManager.getInstance().createModifiableModelsProvider(myProject));
  }


  public List<Module> importProjects(final IdeModifiableModelsProvider modelsProvider) {
    myProject.getMessageBus().syncPublisher(MavenImportListener.TOPIC)
      .importStarted();
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
      StructuredIdeActivity activity = ExternalSystemStatUtilKt.importActivityStarted(myProject, MavenUtil.SYSTEM_ID, () ->
        Collections.singletonList(ProjectImportCollector.TASK_CLASS.with(MavenImportStats.ImportingTaskOld.class))
      );
      try {
        MavenProjectImporter projectImporter = MavenProjectImporter.createImporter(
          myProject, getProjectsTree(), projectsToImportWithChanges, importModuleGroupsRequired,
          modelsProvider, getImportingSettings(), myPreviewModule, activity
        );
        importer.set(projectImporter);
        postTasks.set(projectImporter.importProject());
      }
      finally {
        activity.finished();
      }
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
      fm.asyncRefresh();
    }

    if (postTasks.get() != null /*may be null if importing is cancelled*/) {
      schedulePostImportTasks(postTasks.get());
    }

    // do not block user too often
    myImportingQueue.restartTimer();

    MavenProjectImporter projectImporter = importer.get();
    List<Module> createdModules = projectImporter == null ? Collections.emptyList() : projectImporter.createdModules();
    myProject.getMessageBus().syncPublisher(MavenImportListener.TOPIC)
      .importFinished(projectsToImportWithChanges.keySet(), createdModules);
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

  private void fireProjectScheduled() {
    for (Listener each : myManagerListeners) {
      each.projectsScheduled();
    }
  }

  private void fireImportAndResolveScheduled(MavenImportSpec spec) {
    for (Listener each : myManagerListeners) {
      each.importAndResolveScheduled();
    }
  }


  private void fireProjectImportCompleted() {
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
      runWhenFullyOpen(project, (manager) -> manager.doScheduleUpdateProjects(List.of(), new MavenImportSpec(true, false, false)));
    }

    @Override
    public void markDirty(@NotNull Module module) {
      runWhenFullyOpen(module.getProject(), (manager) -> {
        MavenProject mavenProject = manager.findProject(module);
        if (mavenProject != null) {
          manager.doScheduleUpdateProjects(Collections.singletonList(mavenProject), new MavenImportSpec(true, false, false));
        }
      });
    }

    private static void runWhenFullyOpen(@NotNull Project project, @NotNull Consumer<MavenProjectsManager> consumer) {
      MavenProjectsManager manager = getInstance(project);
      manager.runWhenFullyOpen(() -> consumer.accept(manager));
    }
  }
}
