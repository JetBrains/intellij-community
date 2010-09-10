/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.facade.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenProjectsManager", storages = {@Storage(id = "default", file = "$PROJECT_FILE$")})
public class MavenProjectsManager extends SimpleProjectComponent
  implements PersistentStateComponent<MavenProjectsManagerState>, SettingsSavingComponent {
  private static final int IMPORT_DELAY = 1000;

  static final Object SCHEDULE_IMPORT_MESSAGE = "SCHEDULE_IMPORT_MESSAGE";
  static final Object FORCE_IMPORT_MESSAGE = "FORCE_IMPORT_MESSAGE";

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

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
  private final Map<MavenProject, MavenProjectChanges> myProjectsToImport = new THashMap<MavenProject, MavenProjectChanges>();
  private boolean myImportModuleGroupsRequired = false;

  private MavenMergingUpdateQueue mySchedulesQueue;

  private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher =
    EventDispatcher.create(MavenProjectsTree.Listener.class);
  private final List<Listener> myManagerListeners = ContainerUtil.createEmptyCOWList();
  private ModificationTracker myModificationTracker;

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
    return MavenWorkspaceSettingsComponent.getInstance(myProject).getState();
  }

  public File getLocalRepository() {
    return getGeneralSettings().getEffectiveLocalRepository();
  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;

    StartupManagerEx.getInstanceEx(myProject).registerStartupActivity(new Runnable() {
      public void run() {
        boolean wasMavenized = !myState.originalFiles.isEmpty();
        if (!wasMavenized) return;
        initMavenized();
      }
    });
  }

  private void initMavenized() {
    doInit(false);
  }

  private void initNew(List<VirtualFile> files, List<String> explicitProfiles) {
    myState.originalFiles = MavenUtil.collectPaths(files);
    myState.activeProfiles = explicitProfiles;
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
    myState.activeProfiles = new ArrayList<String>(myProjectsTree.getExplicitProfiles());
    myState.ignoredFiles = new THashSet<String>(myProjectsTree.getIgnoredFilesPaths());
    myState.ignoredPathMasks = myProjectsTree.getIgnoredFilesPatterns();
  }

  private void applyStateToTree() {
    myProjectsTree.resetManagedFilesPathsAndProfiles(myState.originalFiles, myState.activeProfiles);
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
    File file = new File(getProjectsTreesDir(), myProject.getLocationHash() + "/tree.dat");
    file.getParentFile().mkdirs();
    return file;
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

    myWatcher = new MavenProjectsManagerWatcher(myProject, myProjectsTree, getGeneralSettings(), myReadingProcessor, myEmbeddersManager);

    myImportingQueue = new MavenMergingUpdateQueue(getComponentName() + ": Importing queue", IMPORT_DELAY, !isUnitTestMode(), myProject);
    myImportingQueue.setPassThrough(false);

    myImportingQueue.makeUserAware(myProject);
    myImportingQueue.makeDumbAware(myProject);
    myImportingQueue.makeModalAware(myProject);

    mySchedulesQueue = new MavenMergingUpdateQueue(getComponentName() + ": Schedules queue", 500, !isUnitTestMode(), myProject);
    mySchedulesQueue.setPassThrough(false);
  }

  private void listenForSettingsChanges() {
    getImportingSettings().addListener(new MavenImportingSettings.Listener() {
      public void autoImportChanged() {
        scheduleImportSettings();
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
      public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, Object message) {
        if (message instanceof MavenProjectImporter) return;
        scheduleImport(false);
      }

      @Override
      public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted, Object message) {
        myEmbeddersManager.clearCaches();

        unscheduleAllTasks(deleted);

        List<MavenProject> updatedProjects = MavenUtil.collectFirsts(updated);

        // import only updated and the dependents (we need to update faced-deps, packaging etc);
        List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<Pair<MavenProject, MavenProjectChanges>>(updated);
        for (MavenProject each : updatedProjects) {
          for (MavenProject eachDependent : myProjectsTree.getDependentProjects(each)) {
            toImport.add(Pair.create(eachDependent, MavenProjectChanges.DEPENDENCIES));
          }
        }

        // resolve updated, theirs dependents, and dependents of deleted
        Set<MavenProject> toResolve = new THashSet<MavenProject>(updatedProjects);
        for (MavenProject each : ContainerUtil.concat(updatedProjects, deleted)) {
          toResolve.addAll(myProjectsTree.getDependentProjects(each));
        }

        // do not try to resolve projects with syntactic errors
        Iterator<MavenProject> it = toResolve.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          if (each.hasReadingProblems()) it.remove();
        }

        if (haveChanges(toImport) || !deleted.isEmpty()) {
          scheduleImport(toImport, message == FORCE_IMPORT_MESSAGE);
        }
        scheduleResolve(toResolve, message == FORCE_IMPORT_MESSAGE);
      }

      private boolean haveChanges(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
        for (MavenProjectChanges each : MavenUtil.collectSeconds(projectsWithChanges)) {
          if (each.hasChanges()) return true;
        }
        return false;
      }

      @Override
      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  @Nullable NativeMavenProjectHolder nativeMavenProject,
                                  Object message) {
        if (nativeMavenProject != null && shouldScheduleProject(projectWithChanges)) {
          if (projectWithChanges.first.hasUnresolvedPlugins()) {
            schedulePluginsResolving(projectWithChanges.first, nativeMavenProject);
          }
          scheduleArtifactsDownloading(Collections.singleton(projectWithChanges.first),
                                       null,
                                       getImportingSettings().isDownloadSourcesAutomatically(),
                                       getImportingSettings().isDownloadDocsAutomatically(),
                                       null);
          scheduleForNextImport(projectWithChanges);
        }
        processMessage(message);
      }

      @Override
      public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges, Object message) {
        if (shouldScheduleProject(projectWithChanges)) {
          scheduleForNextImport(projectWithChanges);
        }
        processMessage(message);
      }

      private boolean shouldScheduleProject(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        return !projectWithChanges.first.hasReadingProblems() && projectWithChanges.second.hasChanges();
      }

      private void processMessage(Object message) {
        if (getScheduledProjectsCount() == 0) return;

        if (message == SCHEDULE_IMPORT_MESSAGE) {
          scheduleImport(false);
        }
        else if (message == FORCE_IMPORT_MESSAGE) {
          scheduleImport(true);
        }
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

      Disposer.dispose(mySchedulesQueue);
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
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return "true".equals(m.getOptionValue(getMavenizedModuleOptionName()));
      }
    });
  }

  public void setMavenizedModules(Collection<Module> modules, boolean mavenized) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    for (Module m : modules) {
      if (mavenized) {
        m.setOption(getMavenizedModuleOptionName(), "true");
      }
      else {
        m.clearOption(getMavenizedModuleOptionName());
      }
    }
  }

  private String getMavenizedModuleOptionName() {
    return getComponentName() + ".isMavenModule";
  }

  @TestOnly
  public void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, List<String> profiles) {
    myWatcher.resetManagedFilesAndProfilesInTests(files, profiles);
  }

  public void addManagedFilesWithProfiles(List<VirtualFile> files, List<String> profiles) {
    if (!isInitialized()) {
      initNew(files, profiles);
    }
    else {
      myWatcher.addManagedFilesWithProfiles(files, profiles);
    }
  }

  public void addManagedFiles(List<VirtualFile> files) {
    addManagedFilesWithProfiles(files, Collections.<String>emptyList());
  }

  public void removeManagedFiles(List<VirtualFile> files) {
    myWatcher.removeManagedFiles(files);
  }

  public boolean isManagedFile(VirtualFile f) {
    if (!isInitialized()) return false;
    return myProjectsTree.isManagedFile(f);
  }

  public Collection<String> getExplicitProfiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getExplicitProfiles();
  }

  public void setExplicitProfiles(Collection<String> profiles) {
    myWatcher.setExplicitProfiles(profiles);
  }

  public Collection<String> getAvailableProfiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getAvailableProfiles();
  }

  public Collection<Pair<String, MavenProfileKind>> getProfilesWithStates() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProfilesWithStates();
  }

  public boolean hasProjects() {
    if (!isInitialized()) return false;
    return myProjectsTree.hasProjects();
  }

  public List<MavenProject> getProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProjects();
  }

  public List<MavenProject> getNonIgnoredProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getNonIgnoredProjects();
  }

  public List<VirtualFile> getProjectsFiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProjectsFiles();
  }

  public MavenProject findProject(VirtualFile f) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(f);
  }

  public MavenProject findProject(MavenId id) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(id);
  }

  public MavenProject findProject(MavenArtifact artifact) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(artifact);
  }

  public MavenProject findProject(Module module) {
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
  public Module findModule(MavenProject project) {
    if (!isInitialized()) return null;
    return ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(project.getFile());
  }

  @NotNull
  public Set<MavenProject> findInheritors(@Nullable MavenProject parent) {
    if (parent == null || !isInitialized()) return Collections.emptySet();
    return myProjectsTree.findInheritors(parent);
  }

  public MavenProject findContainingProject(VirtualFile file) {
    if (!isInitialized()) return null;
    Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
    return module == null ? null : findProject(module);
  }

  private static VirtualFile findPomFile(Module module, MavenModelsProvider modelsProvider) {
    for (VirtualFile root : modelsProvider.getContentRoots(module)) {
      final VirtualFile virtualFile = root.findChild(MavenConstants.POM_XML);
      if (virtualFile != null) {
        return virtualFile;
      }
    }
    return null;
  }

  public MavenProject findAggregator(MavenProject module) {
    if (!isInitialized()) return null;
    return myProjectsTree.findAggregator(module);
  }

  public List<MavenProject> getModules(MavenProject aggregator) {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getModules(aggregator);
  }

  public List<String> getIgnoredFilesPaths() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getIgnoredFilesPaths();
  }

  public void setIgnoredFilesPaths(List<String> paths) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredFilesPaths(paths);
  }

  public boolean getIgnoredState(MavenProject project) {
    if (!isInitialized()) return false;
    return myProjectsTree.getIgnoredState(project);
  }

  public void setIgnoredState(List<MavenProject> projects, boolean ignored) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredState(projects, ignored);
  }

  public List<String> getIgnoredFilesPatterns() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getIgnoredFilesPatterns();
  }

  public void setIgnoredFilesPatterns(List<String> patterns) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredFilesPatterns(patterns);
  }

  public boolean isIgnored(MavenProject project) {
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

  private void scheduleUpdateAllProjects(boolean forceImport) {
    doScheduleUpdateProjects(null, false, forceImport);
  }

  public void forceUpdateProjects(Collection<MavenProject> projects) {
    doScheduleUpdateProjects(projects, true, true);
  }

  public void forceUpdateAllProjectsOrFindAllAvailablePomFiles() {
    if (!isMavenizedProject()) {
      addManagedFiles(collectAllAvailablePomFiles());
    }
    doScheduleUpdateProjects(null, true, true);
  }

  private void doScheduleUpdateProjects(final Collection<MavenProject> projects, final boolean force, final boolean forceImport) {
    // read when postStartupActivities start
    MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
      public void run() {
        if (projects == null) {
          myWatcher.scheduleUpdateAll(force, forceImport);
        }
        else {
          myWatcher.scheduleUpdate(MavenUtil.collectFiles(projects), Collections.<VirtualFile>emptyList(), force, forceImport);
        }
      }
    });
  }

  private void scheduleResolve(final Collection<MavenProject> projects, final boolean forceImport) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        Iterator<MavenProject> it = projects.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          Object message = it.hasNext() ? null : (forceImport ? FORCE_IMPORT_MESSAGE : SCHEDULE_IMPORT_MESSAGE);
          myResolvingProcessor.scheduleTask(new MavenProjectsProcessorResolvingTask(each, myProjectsTree, getGeneralSettings(), message));
        }
      }
    });
  }

  @TestOnly
  public void scheduleResolveInTests(Collection<MavenProject> projects) {
    scheduleResolve(projects, false);
  }

  @TestOnly
  public void scheduleResolveAllInTests() {
    scheduleResolve(getProjects(), false);
  }

  public void scheduleFoldersResolving(final Collection<MavenProject> projects) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        Iterator<MavenProject> it = projects.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          Object message = it.hasNext() ? null : FORCE_IMPORT_MESSAGE;
          myFoldersResolvingProcessor.scheduleTask(
            new MavenProjectsProcessorFoldersResolvingTask(each, getImportingSettings(), myProjectsTree, message));
        }
      }
    });
  }

  public void scheduleFoldersResolvingForAllProjects() {
    scheduleFoldersResolving(getProjects());
  }

  private void schedulePluginsResolving(final MavenProject project, final NativeMavenProjectHolder nativeMavenProject) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        myPluginsResolvingProcessor
          .scheduleTask(new MavenProjectsProcessorPluginsResolvingTask(project, nativeMavenProject, myProjectsTree));
      }
    });
  }

  public void scheduleArtifactsDownloading(final Collection<MavenProject> projects, @Nullable final Collection<MavenArtifact> artifacts,
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

  private void scheduleImport(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges, boolean forceImport) {
    scheduleForNextImport(projectsWithChanges);
    scheduleImport(forceImport);
  }

  private void scheduleImportSettings() {
    scheduleImportSettings(false);
  }

  private void scheduleImportSettings(boolean importModuleGroupsRequired) {
    synchronized (myImportingDataLock) {
      myImportModuleGroupsRequired = importModuleGroupsRequired;
    }
    scheduleImport(false);
  }

  private void scheduleImport(final boolean forceImport) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        final boolean autoImport = getImportingSettings().isImportAutomatically();
        // postpone activation to prevent import from being called from events poster
        mySchedulesQueue.queue(new Update(new Object()) {
          public void run() {
            if (autoImport || forceImport) {
              myImportingQueue.activate();
            }
            else {
              myImportingQueue.deactivate();
            }
          }
        });
        myImportingQueue.queue(new Update(MavenProjectsManager.this) {
          public void run() {
            importProjects();
            if (!autoImport) myImportingQueue.deactivate();
          }
        });
        if (!forceImport) fireScheduledImportsChanged();
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
    scheduleImport(toImport, false);
  }

  private void scheduleForNextImport(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    scheduleForNextImport(Collections.singletonList(projectWithChanges));
  }

  private void scheduleForNextImport(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
    synchronized (myImportingDataLock) {
      for (Pair<MavenProject, MavenProjectChanges> each : projectsWithChanges) {
        MavenProjectChanges changes = each.second.mergedWith(myProjectsToImport.get(each.first));
        myProjectsToImport.put(each.first, changes);
      }
    }
  }

  public boolean hasScheduledImports() {
    if (!isInitialized()) return false;
    return !myImportingQueue.isEmpty();
  }

  public int getScheduledProjectsCount() {
    if (!isInitialized()) return 0;
    synchronized (myImportingDataLock) {
      return myProjectsToImport.size();
    }
  }

  public void performScheduledImport() {
    performScheduledImport(true);
  }

  public void performScheduledImport(final boolean force) {
    if (!isInitialized()) return;
    runWhenFullyOpen(new Runnable() {
      public void run() {
        // ensure all pending schedules are processed
        mySchedulesQueue.flush(false);
        if (!force && !myImportingQueue.isActive()) return;
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
          mySchedulesQueue.queue(new Update(runnable) { // should not remove previously schedules tasks

            public void run() {
              wrapper.get().run();
            }
          });
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
    MavenUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        MavenFoldersImporter.updateProjectFolders(myProject, targetFoldersOnly);
        VirtualFileManager.getInstance().refresh(false);
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
      projectsToImportWithChanges = new THashMap<MavenProject, MavenProjectChanges>(myProjectsToImport);
      myProjectsToImport.clear();
      importModuleGroupsRequired = myImportModuleGroupsRequired;
    }
    fireScheduledImportsChanged();

    final Ref<MavenProjectImporter> importer = new Ref<MavenProjectImporter>();
    final Ref<List<MavenProjectsProcessorTask>> postTasks = new Ref<List<MavenProjectsProcessorTask>>();

    final Runnable r = new Runnable() {
      public void run() {
        importer.set(
          new MavenProjectImporter(myProject, myProjectsTree, getFileToModuleMapping(modelsProvider), projectsToImportWithChanges,
                                   importModuleGroupsRequired, modelsProvider, getImportingSettings()));
        postTasks.set(importer.get().importProject());
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


    VirtualFileManager.getInstance().refresh(isNormalProject());
    if (postTasks.get() != null /*may be null if importing is cancelled*/)
      schedulePostImportTasks(postTasks.get());

    // do not block user too often
    myImportingQueue.restartTimer();

    return importer.get().getCreatedModules();
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

  public MavenDomDependency addOverridenDependency(final MavenProject mavenProject, final MavenId id) {
     return addDependency(mavenProject, id, true);
  }


  public MavenDomDependency addDependency(final MavenProject mavenProject, final MavenId id) {
    return addDependency(mavenProject, id, false);
  }

  public MavenDomDependency addDependency(final MavenProject mavenProject, final MavenId id, final boolean overridden) {
    final MavenArtifact[] artifact = new MavenArtifact[1];

    try {
      MavenUtil.run(myProject, "Downloading dependency...", new MavenTask() {
        public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
          artifact[0] = MavenProjectsTree.downloadArtifact(mavenProject, id, myEmbeddersManager, new SoutMavenConsole(), indicator);
        }
      });
    }
    catch (MavenProcessCanceledException ignore) {
      return null;
    }

    VirtualFile file = mavenProject.getFile();
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);

    MavenDomDependency result = new WriteCommandAction<MavenDomDependency>(myProject, "Add Maven Dependency", psiFile) {
      protected void run(Result<MavenDomDependency> result) throws Throwable {
        MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());

        MavenDomDependency domDependency = MavenDomUtil.createDomDependency(model, artifact[0], getEditor(), overridden);

        mavenProject.addDependency(artifact[0]);
        result.setResult(domDependency);
      }
    }.execute().getResultObject();

    scheduleImport(Collections.singletonList(Pair.create(mavenProject, MavenProjectChanges.DEPENDENCIES)), true);

    return result;
  }

  @Nullable
  private Editor getEditor() {
    return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
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

  private void fireScheduledImportsChanged() {
    for (Listener each : myManagerListeners) {
      each.scheduledImportsChanged();
    }
  }

  public interface Listener {
    void activated();

    void scheduledImportsChanged();
  }
}
