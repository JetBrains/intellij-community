// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ArrayListSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.io.PathKt;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public final class MavenProjectsTree {
  private static final Logger LOG = Logger.getInstance(MavenProjectsTree.class);

  private static final String STORAGE_VERSION = MavenProjectsTree.class.getSimpleName() + ".7";

  private final Object myStateLock = new Object();
  private final ReentrantReadWriteLock myStructureLock = new ReentrantReadWriteLock();
  private final Lock myStructureReadLock = myStructureLock.readLock();
  private final Lock myStructureWriteLock = myStructureLock.writeLock();

  // TODO replace with sets
  private volatile Set<String> myManagedFilesPaths = new LinkedHashSet<>();
  private volatile List<String> myIgnoredFilesPaths = new ArrayList<>();
  private volatile List<String> myIgnoredFilesPatterns = new ArrayList<>();
  private volatile Pattern myIgnoredFilesPatternsCache;

  private MavenExplicitProfiles myExplicitProfiles = MavenExplicitProfiles.NONE;
  private final MavenExplicitProfiles myTemporarilyRemovedExplicitProfiles =
    new MavenExplicitProfiles(new HashSet<>(), new HashSet<>());

  private final List<MavenProject> myRootProjects = new ArrayList<>();

  private final Map<MavenProject, MavenProjectTimestamp> myTimestamps = new HashMap<>();
  private final MavenWorkspaceMap myWorkspaceMap = new MavenWorkspaceMap();
  private final Map<MavenId, MavenProject> myMavenIdToProjectMapping = new HashMap<>();
  private final Map<VirtualFile, MavenProject> myVirtualFileToProjectMapping = new HashMap<>();
  private final Map<MavenProject, List<MavenProject>> myAggregatorToModuleMapping = new HashMap<>();
  private final Map<MavenProject, MavenProject> myModuleToAggregatorMapping = new HashMap<>();

  private final DisposableWrapperList<Listener> myListeners = new DisposableWrapperList<>();
  private final Project myProject;


  private final MavenProjectReaderProjectLocator myProjectLocator = new MavenProjectReaderProjectLocator() {
    @Override
    public VirtualFile findProjectFile(MavenId coordinates) {
      MavenProject project = findProject(coordinates);
      return project == null ? null : project.getFile();
    }
  };

  public MavenProjectsTree(@NotNull Project project) {
    myProject = project;
  }

  Project getProject() {
    return myProject;
  }


  public MavenProjectReaderProjectLocator getProjectLocator() {
    return myProjectLocator;
  }

  @Nullable
  public static MavenProjectsTree read(Project project, Path file) throws IOException {
    MavenProjectsTree result = new MavenProjectsTree(project);

    try (DataInputStream in = new DataInputStream(new BufferedInputStream(PathKt.inputStream(file)))) {
      try {
        if (!STORAGE_VERSION.equals(in.readUTF())) return null;
        result.myManagedFilesPaths = readCollection(in, new LinkedHashSet<>());
        result.myIgnoredFilesPaths = readCollection(in, new ArrayList<>());
        result.myIgnoredFilesPatterns = readCollection(in, new ArrayList<>());
        result.myExplicitProfiles = new MavenExplicitProfiles(readCollection(in, new HashSet<>()),
                                                              readCollection(in, new HashSet<>()));
        result.myRootProjects.addAll(readProjectsRecursively(in, result));
      }
      catch (IOException e) {
        in.close();
        PathKt.delete(file);
        throw e;
      }
      catch (Throwable e) {
        throw new IOException(e);
      }
    }
    return result;
  }

  private static <T extends Collection<String>> T readCollection(DataInputStream in, T result) throws IOException {
    int count = in.readInt();
    while (count-- > 0) {
      result.add(in.readUTF());
    }
    return result;
  }

  private static void writeCollection(DataOutputStream out, Collection<String> list) throws IOException {
    out.writeInt(list.size());
    for (String each : list) {
      out.writeUTF(each);
    }
  }

  private static List<MavenProject> readProjectsRecursively(DataInputStream in,
                                                            MavenProjectsTree tree) throws IOException {
    int count = in.readInt();
    List<MavenProject> result = new ArrayList<>(count);
    while (count-- > 0) {
      MavenProject project = MavenProject.read(in);
      MavenProjectTimestamp timestamp = MavenProjectTimestamp.read(in);
      List<MavenProject> modules = readProjectsRecursively(in, tree);
      if (project != null) {
        result.add(project);
        tree.myTimestamps.put(project, timestamp);
        tree.myVirtualFileToProjectMapping.put(project.getFile(), project);
        tree.fillIDMaps(project);
        tree.myAggregatorToModuleMapping.put(project, modules);
        for (MavenProject eachModule : modules) {
          tree.myModuleToAggregatorMapping.put(eachModule, project);
        }
      }
    }
    return result;
  }

  public void save(@NotNull Path file) throws IOException {
    synchronized (myStateLock) {
      withReadLock(() -> {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(PathKt.outputStream(file)))) {
          out.writeUTF(STORAGE_VERSION);
          writeCollection(out, myManagedFilesPaths);
          writeCollection(out, myIgnoredFilesPaths);
          writeCollection(out, myIgnoredFilesPatterns);
          writeCollection(out, myExplicitProfiles.getEnabledProfiles());
          writeCollection(out, myExplicitProfiles.getDisabledProfiles());
          writeProjectsRecursively(out, myRootProjects);
        }
      });
    }
  }

  private void writeProjectsRecursively(DataOutputStream out, List<MavenProject> list) throws IOException {
    out.writeInt(list.size());
    for (MavenProject each : list) {
      each.write(out);
      myTimestamps.get(each).write(out);
      writeProjectsRecursively(out, getModules(each));
    }
  }

  @NotNull
  public List<String> getManagedFilesPaths() {
    synchronized (myStateLock) {
      return new ArrayList<>(myManagedFilesPaths);
    }
  }

  public void resetManagedFilesPathsAndProfiles(List<String> paths, MavenExplicitProfiles profiles) {
    synchronized (myStateLock) {
      myManagedFilesPaths = new LinkedHashSet<>(paths);
    }
    setExplicitProfiles(profiles);
  }

  @TestOnly
  public void resetManagedFilesAndProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles) {
    resetManagedFilesPathsAndProfiles(MavenUtil.collectPaths(files), profiles);
  }

  public void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles) {
    List<String> newFiles;
    MavenExplicitProfiles newProfiles;
    synchronized (myStateLock) {
      newFiles = new ArrayList<>(myManagedFilesPaths);
      newFiles.addAll(MavenUtil.collectPaths(files));

      newProfiles = myExplicitProfiles.clone();
      newProfiles.getEnabledProfiles().addAll(profiles.getEnabledProfiles());
      newProfiles.getDisabledProfiles().addAll(profiles.getDisabledProfiles());
    }

    resetManagedFilesPathsAndProfiles(newFiles, newProfiles);
  }

  public void removeManagedFiles(List<VirtualFile> files) {
    synchronized (myStateLock) {
      myManagedFilesPaths.removeAll(MavenUtil.collectPaths(files));
    }
  }

  public List<VirtualFile> getExistingManagedFiles() {
    List<VirtualFile> result = new ArrayList<>();
    for (String path : getManagedFilesPaths()) {
      VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path);
      if (f != null && f.exists()) result.add(f);
    }
    return result;
  }

  public List<String> getIgnoredFilesPaths() {
    synchronized (myStateLock) {
      return new ArrayList<>(myIgnoredFilesPaths);
    }
  }

  public void setIgnoredFilesPaths(final List<String> paths) {
    doChangeIgnoreStatus(() -> myIgnoredFilesPaths = new ArrayList<>(paths));
  }

  public void removeIgnoredFilesPaths(final Collection<String> paths) {
    doChangeIgnoreStatus(() -> myIgnoredFilesPaths.removeAll(paths));
  }

  public boolean getIgnoredState(MavenProject project) {
    synchronized (myStateLock) {
      return myIgnoredFilesPaths.contains(project.getPath());
    }
  }

  public void setIgnoredState(List<MavenProject> projects, boolean ignored) {
    setIgnoredState(projects, ignored, false);
  }

  public void setIgnoredState(List<MavenProject> projects, boolean ignored, boolean fromImport) {
    final List<String> pomPaths = ContainerUtil.map(projects, project -> project.getPath());
    setIgnoredStateForPoms(pomPaths, ignored, fromImport);
  }

  @ApiStatus.Internal
  public void setIgnoredStateForPoms(List<String> pomPaths, boolean ignored) {
    doSetIgnoredState(pomPaths, ignored, false);
  }

  @ApiStatus.Internal
  public void setIgnoredStateForPoms(List<String> pomPaths, boolean ignored, boolean fromImport) {
    doSetIgnoredState(pomPaths, ignored, fromImport);
  }

  private void doSetIgnoredState(List<String> pomPaths, final boolean ignored, boolean fromImport) {
    doChangeIgnoreStatus(() -> {
      if (ignored) {
        myIgnoredFilesPaths.addAll(pomPaths);
      }
      else {
        myIgnoredFilesPaths.removeAll(pomPaths);
      }
    }, fromImport);
  }

  public List<String> getIgnoredFilesPatterns() {
    synchronized (myStateLock) {
      return new ArrayList<>(myIgnoredFilesPatterns);
    }
  }

  public void setIgnoredFilesPatterns(final List<String> patterns) {
    doChangeIgnoreStatus(() -> {
      myIgnoredFilesPatternsCache = null;
      myIgnoredFilesPatterns = new ArrayList<>(patterns);
    });
  }

  private void doChangeIgnoreStatus(Runnable runnable) {
    doChangeIgnoreStatus(runnable, false);
  }

  private void doChangeIgnoreStatus(Runnable runnable, boolean fromImport) {
    List<MavenProject> ignoredBefore;
    List<MavenProject> ignoredAfter;

    synchronized (myStateLock) {
      ignoredBefore = getIgnoredProjects();
      runnable.run();
      ignoredAfter = getIgnoredProjects();
    }

    List<MavenProject> ignored = new ArrayList<>(ignoredAfter);
    ignored.removeAll(ignoredBefore);

    List<MavenProject> unignored = new ArrayList<>(ignoredBefore);
    unignored.removeAll(ignoredAfter);

    if (ignored.isEmpty() && unignored.isEmpty()) return;

    fireProjectsIgnoredStateChanged(ignored, unignored, fromImport);
  }

  private List<MavenProject> getIgnoredProjects() {
    List<MavenProject> result = new ArrayList<>();
    for (MavenProject each : getProjects()) {
      if (isIgnored(each)) result.add(each);
    }
    return result;
  }

  public boolean isIgnored(MavenProject project) {
    String path = project.getPath();
    synchronized (myStateLock) {
      return myIgnoredFilesPaths.contains(path) || matchesIgnoredFilesPatterns(path);
    }
  }

  private boolean matchesIgnoredFilesPatterns(String path) {
    synchronized (myStateLock) {
      if (myIgnoredFilesPatternsCache == null) {
        myIgnoredFilesPatternsCache = Pattern.compile(Strings.translateMasks(myIgnoredFilesPatterns));
      }
      return myIgnoredFilesPatternsCache.matcher(path).matches();
    }
  }

  public MavenExplicitProfiles getExplicitProfiles() {
    synchronized (myStateLock) {
      return myExplicitProfiles.clone();
    }
  }

  public void setExplicitProfiles(MavenExplicitProfiles explicitProfiles) {
    synchronized (myStateLock) {
      myExplicitProfiles = explicitProfiles.clone();
    }
    fireProfilesChanged();
  }

  private void updateExplicitProfiles() {
    Collection<String> available = getAvailableProfiles();

    synchronized (myStateLock) {
      updateExplicitProfiles(myExplicitProfiles.getEnabledProfiles(), myTemporarilyRemovedExplicitProfiles.getEnabledProfiles(),
                             available);
      updateExplicitProfiles(myExplicitProfiles.getDisabledProfiles(), myTemporarilyRemovedExplicitProfiles.getDisabledProfiles(),
                             available);
    }
  }

  private static void updateExplicitProfiles(Collection<String> explicitProfiles, Collection<String> temporarilyRemovedExplicitProfiles,
                                             Collection<String> available) {
    Collection<String> removedProfiles = new HashSet<>(explicitProfiles);
    removedProfiles.removeAll(available);
    temporarilyRemovedExplicitProfiles.addAll(removedProfiles);

    Collection<String> restoredProfiles = new HashSet<>(temporarilyRemovedExplicitProfiles);
    restoredProfiles.retainAll(available);
    temporarilyRemovedExplicitProfiles.removeAll(restoredProfiles);

    explicitProfiles.removeAll(removedProfiles);
    explicitProfiles.addAll(restoredProfiles);
  }

  public Collection<String> getAvailableProfiles() {
    Collection<String> res = new HashSet<>();

    for (MavenProject each : getProjects()) {
      res.addAll(each.getProfilesIds());
    }

    return res;
  }

  public Collection<Pair<String, MavenProfileKind>> getProfilesWithStates() {
    Collection<Pair<String, MavenProfileKind>> result = new ArrayListSet<>();

    Collection<String> available = new HashSet<>();
    Collection<String> active = new HashSet<>();
    for (MavenProject each : getProjects()) {
      available.addAll(each.getProfilesIds());
      active.addAll(each.getActivatedProfilesIds().getEnabledProfiles());
    }

    Collection<String> enabledProfiles = getExplicitProfiles().getEnabledProfiles();
    Collection<String> disabledProfiles = getExplicitProfiles().getDisabledProfiles();

    for (String each : available) {
      MavenProfileKind state;
      if (disabledProfiles.contains(each)) {
        state = MavenProfileKind.NONE;
      }
      else if (enabledProfiles.contains(each)) {
        state = MavenProfileKind.EXPLICIT;
      }
      else if (active.contains(each)) {
        state = MavenProfileKind.IMPLICIT;
      }
      else {
        state = MavenProfileKind.NONE;
      }
      result.add(Pair.create(each, state));
    }
    return result;
  }

  public void updateAll(boolean force, MavenGeneralSettings generalSettings, MavenProgressIndicator process) {
    List<VirtualFile> managedFiles = getExistingManagedFiles();
    MavenExplicitProfiles explicitProfiles = getExplicitProfiles();

    MavenProjectReader projectReader = new MavenProjectReader(myProject);
    update(managedFiles, true, force, explicitProfiles, projectReader, generalSettings, process);

    Collection<VirtualFile> obsoleteFiles = ContainerUtil.subtract(getRootProjectsFiles(), managedFiles);
    delete(projectReader, obsoleteFiles, explicitProfiles, generalSettings, process);
  }

  public void update(Collection<VirtualFile> files,
                     boolean force,
                     MavenGeneralSettings generalSettings,
                     MavenProgressIndicator process) {
    update(files, false, force, getExplicitProfiles(), new MavenProjectReader(myProject), generalSettings, process);
  }

  private void update(final Collection<VirtualFile> files,
                      final boolean updateModules,
                      final boolean forceRead,
                      final MavenExplicitProfiles explicitProfiles,
                      final MavenProjectReader projectReader,
                      final MavenGeneralSettings generalSettings,
                      final MavenProgressIndicator process) {
    if (files.isEmpty()) return;

    UpdateContext updateContext = new UpdateContext();

    var updater = new MavenProjectsTreeUpdater(this, explicitProfiles, updateContext, projectReader, generalSettings, process, updateModules);
    var filesToAddModules = new HashSet<VirtualFile>();
    for (VirtualFile file : files) {
      if (null == findProject(file)) {
        filesToAddModules.add(file);
      }
      updater.updateProjects(List.of(new UpdateSpec(file, forceRead)));
    }

    for (MavenProject aggregator : getProjects()) {
      for (VirtualFile moduleFile : aggregator.getExistingModuleFiles()) {
        if (filesToAddModules.contains(moduleFile)) {
          filesToAddModules.remove(moduleFile);
          var mavenProject = findProject(moduleFile);
          if (null != mavenProject) {
            if (reconnect(aggregator, mavenProject)) {
              updateContext.update(mavenProject, MavenProjectChanges.NONE);
            }
          }
        }
      }
    }

    for (VirtualFile file : filesToAddModules) {
      var mavenProject = findProject(file);
      if (null != mavenProject) {
        addRootModule(mavenProject);
      }
    }

    updateExplicitProfiles();
    updateContext.fireUpdatedIfNecessary();
  }

  private record UpdateSpec(VirtualFile mavenProjectFile, boolean forceRead) {
  }

  private static class MavenProjectsTreeUpdater {
    private final MavenProjectsTree tree;
    private final MavenExplicitProfiles explicitProfiles;
    private final UpdateContext updateContext;
    private final MavenProjectReader reader;
    private final MavenGeneralSettings generalSettings;
    private final MavenProgressIndicator process;
    private final ConcurrentHashMap<VirtualFile, Boolean> updated = new ConcurrentHashMap<>();
    private final boolean updateModules;

    MavenProjectsTreeUpdater(MavenProjectsTree tree,
                             MavenExplicitProfiles profiles,
                             UpdateContext context,
                             MavenProjectReader reader,
                             MavenGeneralSettings settings,
                             MavenProgressIndicator process,
                             boolean updateModules) {
      this.tree = tree;
      explicitProfiles = profiles;
      updateContext = context;
      this.reader = reader;
      generalSettings = settings;
      this.process = process;
      this.updateModules = updateModules;
    }

    private boolean startUpdate(VirtualFile mavenProjectFile, boolean forceRead) {
      Ref<Boolean> previousUpdateRef = new Ref<>();
      updated.compute(mavenProjectFile, (file, value) -> {
        previousUpdateRef.set(value);
        return Boolean.TRUE.equals(value) || forceRead;
      });
      var previousUpdate = previousUpdateRef.get();

      if ((null != previousUpdate && !forceRead) || Boolean.TRUE.equals(previousUpdate)) {
        // we already updated this file
        MavenLog.LOG.debug("Has already been updated (%s): %s; forceRead: %s".formatted(previousUpdate, mavenProjectFile, forceRead));
        return false;
      }
      process.setText(MavenProjectBundle.message("maven.reading.pom", mavenProjectFile.getPath()));
      process.setText2("");
      return true;
    }

    private boolean readPomIfNeeded(MavenProject mavenProject, boolean forceRead) {
      var timestamp = tree.calculateTimestamp(mavenProject, explicitProfiles, generalSettings);
      boolean timeStampChanged = !timestamp.equals(tree.myTimestamps.get(mavenProject));
      boolean readPom = forceRead || timeStampChanged;

      if (readPom) {
        MavenId oldProjectId = mavenProject.isNew() ? null : mavenProject.getMavenId();
        MavenId oldParentId = mavenProject.getParentId();
        var readChanges = mavenProject.read(generalSettings, explicitProfiles, reader, tree.myProjectLocator);
        tree.withWriteLock(() -> {
          tree.clearIDMaps(oldProjectId);
          tree.myVirtualFileToProjectMapping.put(mavenProject.getFile(), mavenProject);
          tree.fillIDMaps(mavenProject);
        });

        if (Comparing.equal(oldParentId, mavenProject.getParentId())) {
          tree.myTimestamps.put(mavenProject, timestamp);
        }
        else {
          // ensure timestamp reflects actual parent's timestamp
          var newTimestamp = tree.calculateTimestamp(mavenProject, explicitProfiles, generalSettings);
          tree.myTimestamps.put(mavenProject, newTimestamp);
        }

        var forcedChanges = forceRead ? MavenProjectChanges.ALL : MavenProjectChanges.NONE;
        var changes = MavenProjectChangesBuilder.merged(forcedChanges, readChanges);
        updateContext.update(mavenProject, changes);
      }

      return readPom;
    }

    private void handleRemovedModules(MavenProject mavenProject, List<MavenProject> prevModules, List<VirtualFile> existingModuleFiles) {
      var removedModules = ContainerUtil.filter(prevModules, prevModule -> !existingModuleFiles.contains(prevModule.getFile()));
      for (MavenProject module : removedModules) {
        VirtualFile moduleFile = module.getFile();
        if (tree.isManagedFile(moduleFile)) {
          if (tree.reconnectRoot(module)) {
            updateContext.update(module, MavenProjectChanges.NONE);
          }
        }
        else {
          tree.removeModule(mavenProject, module);
          tree.doDelete(mavenProject, module, updateContext);
        }
      }
    }

    private void reconnectModuleFiles(MavenProject mavenProject, List<VirtualFile> modulesFilesToReconnect) {
      for (var file : modulesFilesToReconnect) {
        MavenProject module = tree.findProject(file);
        if (null != module) {
          if (tree.reconnect(mavenProject, module)) {
            updateContext.update(module, MavenProjectChanges.NONE);
          }
        }
      }
    }

    private List<VirtualFile> collectModuleFilesToReconnect(MavenProject mavenProject, List<VirtualFile> existingModuleFiles) {
      var modulesFilesToReconnect = new ArrayList<VirtualFile>();
      for (VirtualFile moduleFile : existingModuleFiles) {
        MavenProject foundModule = tree.findProject(moduleFile);
        boolean isNewModule = foundModule == null;
        if (!isNewModule) {
          MavenProject currentAggregator = tree.findAggregator(foundModule);
          if (currentAggregator != null && currentAggregator != mavenProject) {
            MavenLog.LOG.info("Module " + moduleFile + " is already included into " + mavenProject.getFile());
            continue;
          }
        }

        modulesFilesToReconnect.add(moduleFile);
      }
      return modulesFilesToReconnect;
    }

    private List<VirtualFile> collectModuleFilesToUpdate(List<VirtualFile> moduleFilesToReconnect, boolean updateExistingModules) {
      if (updateExistingModules) {
        return moduleFilesToReconnect;
      }

      // update only new modules
      return ContainerUtil.filter(moduleFilesToReconnect, moduleFile -> null == tree.findProject(moduleFile));
    }

    private List<VirtualFile> collectChildFilesToUpdate(MavenProject mavenProject, Collection<MavenProject> prevChildren) {
      var children = new HashSet<>(prevChildren);
      children.removeAll(updateContext.getDeletedProjects());
      children.addAll(tree.findInheritors(mavenProject));
      return ContainerUtil.map(children, child -> child.getFile());
    }

    private void update(final VirtualFile mavenProjectFile, final boolean forceRead) {
      // if the file has already been updated, skip subsequent updates
      if (!startUpdate(mavenProjectFile, forceRead)) return;

      var mavenProject = tree.findOrCreateProject(mavenProjectFile);

      // we will compare modules and children before and after reading the pom.xml file
      var prevModules = tree.getModules(mavenProject);
      var prevChildren = tree.findInheritors(mavenProject);

      // read pom.xml if something has changed since the last reading or reading is forced
      boolean readPom = readPomIfNeeded(mavenProject, forceRead);

      var existingModuleFiles = mavenProject.getExistingModuleFiles();

      // some modules might have been removed
      handleRemovedModules(mavenProject, prevModules, existingModuleFiles);

      // collect new and existing modules to reconnect to the tree
      var modulesFilesToReconnect = collectModuleFilesToReconnect(mavenProject, existingModuleFiles);
      boolean updateExistingModules = readPom || updateModules;

      // collect modules to update recursively
      var modulesFilesToUpdate = collectModuleFilesToUpdate(modulesFilesToReconnect, updateExistingModules);

      // do not force update modules if only this project was requested to be updated
      var forceReadModules = updateModules && forceRead;
      var moduleUpdates = ContainerUtil.map(modulesFilesToUpdate, moduleFile ->
        new UpdateSpec(
          moduleFile,
          forceReadModules
        ));
      updateProjects(moduleUpdates);
      reconnectModuleFiles(mavenProject, modulesFilesToReconnect);

      // collect children (inheritors) to update recursively
      var childFilesToUpdate = collectChildFilesToUpdate(mavenProject, prevChildren);
      var childUpdates = ContainerUtil.map(childFilesToUpdate, childFile ->
        new UpdateSpec(
          childFile,
          readPom // if parent was read, force read children
        ));
      updateProjects(childUpdates);
    }

    public void updateProjects(@NotNull List<UpdateSpec> specs) {
      if (specs.isEmpty()) return;

      ParallelRunner.runInParallel(specs, spec -> {
        update(spec.mavenProjectFile(), spec.forceRead());
      });
    }
  }

  private MavenProjectTimestamp calculateTimestamp(final MavenProject mavenProject,
                                                   final MavenExplicitProfiles explicitProfiles,
                                                   final MavenGeneralSettings generalSettings) {
    return ReadAction.compute(() -> {
      long pomTimestamp = getFileTimestamp(mavenProject.getFile());
      MavenProject parent = findParent(mavenProject);
      long parentLastReadStamp = parent == null ? -1 : parent.getLastReadStamp();
      VirtualFile profilesXmlFile = mavenProject.getProfilesXmlFile();
      long profilesTimestamp = getFileTimestamp(profilesXmlFile);
      VirtualFile jvmConfigFile = MavenUtil.getConfigFile(mavenProject, MavenConstants.JVM_CONFIG_RELATIVE_PATH);
      long jvmConfigTimestamp = getFileTimestamp(jvmConfigFile);
      VirtualFile mavenConfigFile = MavenUtil.getConfigFile(mavenProject, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
      long mavenConfigTimestamp = getFileTimestamp(mavenConfigFile);

      long userSettingsTimestamp = getFileTimestamp(generalSettings.getEffectiveUserSettingsFile());
      long globalSettingsTimestamp = getFileTimestamp(generalSettings.getEffectiveGlobalSettingsFile());

      int profilesHashCode = explicitProfiles.hashCode();

      return new MavenProjectTimestamp(pomTimestamp,
                                       parentLastReadStamp,
                                       profilesTimestamp,
                                       userSettingsTimestamp,
                                       globalSettingsTimestamp,
                                       profilesHashCode,
                                       jvmConfigTimestamp,
                                       mavenConfigTimestamp);
    });
  }

  @Override
  public String toString() {
    return "MavenProjectsTree{" +
           "myRootProjects=" + myRootProjects +
           ", myProject=" + myProject +
           '}';
  }

  private static long getFileTimestamp(VirtualFile file) {
    if (file == null || !file.isValid()) return -1;
    return file.getTimeStamp();
  }

  public boolean isManagedFile(VirtualFile moduleFile) {
    return isManagedFile(moduleFile.getPath());
  }

  public boolean isManagedFile(String path) {
    synchronized (myStateLock) {
      for (String each : myManagedFilesPaths) {
        if (FileUtil.pathsEqual(each, path)) return true;
      }
      return false;
    }
  }

  public boolean isPotentialProject(String path) {
    if (isManagedFile(path)) return true;

    for (MavenProject each : getProjects()) {
      if (VfsUtilCore.pathEqualsTo(each.getFile(), path)) return true;
      if (each.getModulePaths().contains(path)) return true;
    }
    return false;
  }

  public void delete(List<VirtualFile> files,
                     MavenGeneralSettings generalSettings,
                     MavenProgressIndicator process) {
    delete(new MavenProjectReader(myProject), files, getExplicitProfiles(), generalSettings, process);
  }

  private void delete(MavenProjectReader projectReader,
                      Collection<VirtualFile> files,
                      MavenExplicitProfiles explicitProfiles,
                      MavenGeneralSettings generalSettings,
                      MavenProgressIndicator process) {
    if (files.isEmpty()) return;

    UpdateContext updateContext = new UpdateContext();

    Set<MavenProject> inheritorsToUpdate = new HashSet<>();
    for (VirtualFile each : files) {
      MavenProject mavenProject = findProject(each);
      if (mavenProject == null) return;

      inheritorsToUpdate.addAll(findInheritors(mavenProject));
      doDelete(findAggregator(mavenProject), mavenProject, updateContext);
    }
    inheritorsToUpdate.removeAll(updateContext.getDeletedProjects());

    var updater = new MavenProjectsTreeUpdater(this, explicitProfiles, updateContext, projectReader, generalSettings, process, false);
    var updateSpecs = new ArrayList<UpdateSpec>();
    for (MavenProject mavenProject : inheritorsToUpdate) {
      updateSpecs.add(new UpdateSpec(mavenProject.getFile(), false));
    }
    updater.updateProjects(updateSpecs);

    for (MavenProject mavenProject : inheritorsToUpdate) {
      if (reconnectRoot(mavenProject)) {
        updateContext.update(mavenProject, MavenProjectChanges.NONE);
      }
    }
    updateExplicitProfiles();
    updateContext.fireUpdatedIfNecessary();
  }

  private void doDelete(MavenProject aggregator, MavenProject project, UpdateContext updateContext) {
    for (MavenProject each : getModules(project)) {
      if (isManagedFile(each.getPath())) {
        if (reconnectRoot(each)) {
          updateContext.update(each, MavenProjectChanges.NONE);
        }
      }
      else {
        doDelete(project, each, updateContext);
      }
    }

    withWriteLock(() -> {
      if (aggregator != null) {
        removeModule(aggregator, project);
      }
      else {
        myRootProjects.remove(project);
      }
      myTimestamps.remove(project);
      myVirtualFileToProjectMapping.remove(project.getFile());
      clearIDMaps(project.getMavenId());
      myAggregatorToModuleMapping.remove(project);
      myModuleToAggregatorMapping.remove(project);
    });
    updateContext.deleted(project);
  }

  private void fillIDMaps(MavenProject mavenProject) {
    MavenId id = mavenProject.getMavenId();
    myWorkspaceMap.register(id, new File(mavenProject.getFile().getPath()));
    myMavenIdToProjectMapping.put(id, mavenProject);
  }

  private void clearIDMaps(MavenId mavenId) {
    if (null == mavenId) return;

    myWorkspaceMap.unregister(mavenId);
    myMavenIdToProjectMapping.remove(mavenId);
  }

  private static Path mavenProjectToNioPath(@NotNull MavenProject mavenProject) {
    return Path.of(mavenProject.getFile().getParent().getPath());
  }

  private void addRootModule(@NotNull MavenProject project) {
    withWriteLock(() -> {
      myRootProjects.add(project);
      myRootProjects.sort(Comparator.comparing(MavenProjectsTree::mavenProjectToNioPath));
    });
  }

  private boolean reconnect(@NotNull MavenProject newAggregator, @NotNull MavenProject project) {
    MavenProject prevAggregator = findAggregator(project);

    if (prevAggregator == newAggregator) return false;

    withWriteLock(() -> {
      if (prevAggregator != null) {
        removeModule(prevAggregator, project);
      }
      else {
        myRootProjects.remove(project);
      }

      addModule(newAggregator, project);
    });

    return true;
  }

  private boolean reconnectRoot(@NotNull MavenProject project) {
    MavenProject prevAggregator = findAggregator(project);

    if (prevAggregator == null) return false;

    withWriteLock(() -> {
      removeModule(prevAggregator, project);
      addRootModule(project);
    });

    return true;
  }

  public boolean hasProjects() {
    return withReadLock(() -> !myRootProjects.isEmpty());
  }

  public List<MavenProject> getRootProjects() {
    return withReadLock(() -> new ArrayList<>(myRootProjects));
  }

  private static void updateCrc(CRC32 crc, int x) {
    crc.update(x & 0xFF);
    x >>>= 8;
    crc.update(x & 0xFF);
    x >>>= 8;
    crc.update(x & 0xFF);
    x >>>= 8;
    crc.update(x);
  }

  private static void updateCrc(CRC32 crc, long l) {
    updateCrc(crc, (int)l);
    updateCrc(crc, (int)(l >>> 32));
  }

  private static void updateCrc(CRC32 crc, @Nullable String s) {
    if (s == null) {
      crc.update(111);
    }
    else {
      updateCrc(crc, s.hashCode());
      crc.update(s.length() & 0xFF);
    }
  }

  @NotNull
  public static Collection<String> getFilterExclusions(MavenProject mavenProject) {
    Element config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
    if (config == null) {
      return Collections.emptySet();
    }
    final List<String> customNonFilteredExtensions =
      MavenJDOMUtil.findChildrenValuesByPath(config, "nonFilteredFileExtensions", "nonFilteredFileExtension");
    if (customNonFilteredExtensions.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableList(customNonFilteredExtensions);
  }

  public int getFilterConfigCrc(@NotNull ProjectFileIndex fileIndex) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    return withReadLock(() -> {
      final CRC32 crc = new CRC32();

      MavenExplicitProfiles profiles = myExplicitProfiles;
      if (profiles != null) {
        updateCrc(crc, profiles.hashCode());
      }

      Collection<MavenProject> allProjects = myVirtualFileToProjectMapping.values();

      crc.update(allProjects.size() & 0xFF);
      for (MavenProject mavenProject : allProjects) {
        VirtualFile pomFile = mavenProject.getFile();
        Module module = fileIndex.getModuleForFile(pomFile);
        if (module == null) continue;

        if (!Comparing.equal(fileIndex.getContentRootForFile(pomFile), pomFile.getParent())) continue;

        updateCrc(crc, module.getName());

        MavenId mavenId = mavenProject.getMavenId();
        updateCrc(crc, mavenId.getGroupId());
        updateCrc(crc, mavenId.getArtifactId());
        updateCrc(crc, mavenId.getVersion());

        MavenId parentId = mavenProject.getParentId();
        if (parentId != null) {
          updateCrc(crc, parentId.getGroupId());
          updateCrc(crc, parentId.getArtifactId());
          updateCrc(crc, parentId.getVersion());
        }


        updateCrc(crc, mavenProject.getDirectory());
        updateCrc(crc, MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject).pattern());
        updateCrc(crc, mavenProject.getModelMap().hashCode());
        updateCrc(crc, mavenProject.getResources().hashCode());
        updateCrc(crc, mavenProject.getTestResources().hashCode());
        updateCrc(crc, getFilterExclusions(mavenProject).hashCode());
        updateCrc(crc, mavenProject.getProperties().hashCode());

        for (String each : mavenProject.getFilterPropertiesFiles()) {
          File file = new File(each);
          updateCrc(crc, file.lastModified());
        }

        XMLOutputter outputter = new XMLOutputter(Format.getCompactFormat());

        Writer crcWriter = new Writer() {
          @Override
          public void write(char[] cbuf, int off, int len) {
            for (int i = off, end = off + len; i < end; i++) {
              crc.update(cbuf[i]);
            }
          }

          @Override
          public void flush() {

          }

          @Override
          public void close() {

          }
        };

        try {
          Element resourcePluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
          if (resourcePluginCfg != null) {
            outputter.output(resourcePluginCfg, crcWriter);
          }

          Element warPluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-war-plugin");
          if (warPluginCfg != null) {
            outputter.output(warPluginCfg, crcWriter);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      return (int)crc.getValue();
    });
  }

  public List<VirtualFile> getRootProjectsFiles() {
    return MavenUtil.collectFiles(getRootProjects());
  }

  public List<MavenProject> getProjects() {
    return withReadLock(() -> new ArrayList<>(myVirtualFileToProjectMapping.values()));
  }

  public List<MavenProject> getNonIgnoredProjects() {
    return withReadLock(() -> {
      List<MavenProject> result = new ArrayList<>();
      for (MavenProject each : myVirtualFileToProjectMapping.values()) {
        if (!isIgnored(each)) result.add(each);
      }
      return result;
    });
  }

  public List<VirtualFile> getProjectsFiles() {
    return withReadLock(() -> new ArrayList<>(myVirtualFileToProjectMapping.keySet()));
  }

  @NotNull
  private MavenProject findOrCreateProject(VirtualFile f) {
    var mavenProject = findProject(f);
    return null == mavenProject ? new MavenProject(f) : mavenProject;
  }

  @Nullable
  public MavenProject findProject(VirtualFile f) {
    return withReadLock(() -> myVirtualFileToProjectMapping.get(f));
  }

  @Nullable
  public MavenProject findProject(MavenId id) {
    return withReadLock(() -> myMavenIdToProjectMapping.get(id));
  }

  @Nullable
  public MavenProject findProject(MavenArtifact artifact) {
    return findProject(artifact.getMavenId());
  }

  public MavenProject findSingleProjectInReactor(MavenId id) {
    return withReadLock(() -> StreamEx.ofValues(myMavenIdToProjectMapping)
      .collect(MoreCollectors.onlyOne(it -> StringUtil.equals(it.getMavenId().getArtifactId(), id.getArtifactId()) &&
                                            StringUtil.equals(it.getMavenId().getGroupId(), id.getGroupId())))
      .orElse(null));
  }

  MavenWorkspaceMap getWorkspaceMap() {
    return withReadLock(() -> myWorkspaceMap.copy());
  }

  public MavenProject findAggregator(MavenProject project) {
    return withReadLock(() -> myModuleToAggregatorMapping.get(project));
  }

  @NotNull
  public MavenProject findRootProject(@NotNull MavenProject project) {
    return withReadLock(() -> {
      MavenProject rootProject = project;
      while (true) {
        MavenProject aggregator = myModuleToAggregatorMapping.get(rootProject);
        if (aggregator == null) {
          return rootProject;
        }
        rootProject = aggregator;
      }
    });
  }

  public boolean isRootProject(@NotNull MavenProject project) {
    return withReadLock(() -> myModuleToAggregatorMapping.get(project) == null);
  }

  public List<MavenProject> getModules(MavenProject aggregator) {
    return withReadLock(() -> {
      List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
      return modules == null
             ? Collections.emptyList()
             : new ArrayList<>(modules);
    });
  }

  private void addModule(@NotNull MavenProject aggregator, @NotNull MavenProject module) {
    withWriteLock(() -> {
      List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
      if (modules == null) {
        modules = new ArrayList<>();
        myAggregatorToModuleMapping.put(aggregator, modules);
      }
      modules.add(module);

      myModuleToAggregatorMapping.put(module, aggregator);
    });
  }

  private void removeModule(MavenProject aggregator, MavenProject module) {
    withWriteLock(() -> {
      List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
      if (modules == null) return;
      modules.remove(module);
      myModuleToAggregatorMapping.remove(module);
    });
  }

  private MavenProject findParent(MavenProject project) {
    return findProject(project.getParentId());
  }

  public Collection<MavenProject> findInheritors(MavenProject project) {
    if (project.isNew()) return List.of();

    return withReadLock(() -> {
      List<MavenProject> result = null;
      MavenId id = project.getMavenId();

      for (MavenProject each : myVirtualFileToProjectMapping.values()) {
        if (each == project) continue;
        if (id.equals(each.getParentId())) {
          if (result == null) result = new ArrayList<>();
          result.add(each);
        }
      }

      return result == null ? List.of() : result;
    });
  }

  public List<MavenProject> getDependentProjects(Collection<MavenProject> projects) {
    return withReadLock(() -> {
      List<MavenProject> result = null;

      Set<MavenCoordinate> projectIds = new ObjectOpenCustomHashSet<>(projects.size(), new MavenCoordinateHashCodeStrategy());
      for (MavenProject project : projects) {
        projectIds.add(project.getMavenId());
      }

      Set<File> projectPaths = FileCollectionFactory.createCanonicalFileSet();
      for (MavenProject project : projects) {
        projectPaths.add(new File(project.getFile().getPath()));
      }

      for (MavenProject project : myVirtualFileToProjectMapping.values()) {
        boolean isDependent = false;

        Set<String> pathsInStack = project.getModulePaths();
        for (final String path : pathsInStack) {
          if (projectPaths.contains(new File(path))) {
            isDependent = true;
            break;
          }
        }

        if (!isDependent) {
          for (MavenArtifact dep : project.getDependencies()) {
            if (projectIds.contains(dep)) {
              isDependent = true;
              break;
            }
          }
        }

        if (isDependent) {
          if (result == null) result = new ArrayList<>();
          result.add(project);
        }
      }

      return result == null ? Collections.emptyList() : result;
    });
  }

  public <Result> Result visit(Visitor<Result> visitor) {
    for (MavenProject each : getRootProjects()) {
      if (visitor.isDone()) break;
      doVisit(each, visitor);
    }
    return visitor.getResult();
  }

  private <Result> void doVisit(MavenProject project, Visitor<Result> visitor) {
    if (!visitor.isDone() && visitor.shouldVisit(project)) {
      visitor.visit(project);
      for (MavenProject each : getModules(project)) {
        if (visitor.isDone()) break;
        doVisit(each, visitor);
      }
      visitor.leave(project);
    }
  }

  public void addListener(@NotNull Listener l, @NotNull Disposable disposable) {
    myListeners.add(l, disposable);
  }

  @ApiStatus.Internal
  void addListenersFrom(MavenProjectsTree other) {
    myListeners.addAll(other.myListeners);
  }

  void fireProfilesChanged() {
    for (Listener each : myListeners) {
      each.profilesChanged();
    }
  }

  void fireProjectsIgnoredStateChanged(@NotNull List<MavenProject> ignored, @NotNull List<MavenProject> unignored, boolean fromImport) {
    for (Listener each : myListeners) {
      each.projectsIgnoredStateChanged(ignored, unignored, fromImport);
    }
  }

  void fireProjectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
    for (Listener each : myListeners) {
      each.projectsUpdated(updated, deleted);
    }
  }

  void fireProjectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                           @Nullable NativeMavenProjectHolder nativeMavenProject) {
    for (Listener each : myListeners) {
      each.projectResolved(projectWithChanges, nativeMavenProject);
    }
  }

  void firePluginsResolved(@NotNull MavenProject project) {
    for (Listener each : myListeners) {
      each.pluginsResolved(project);
    }
  }

  void fireFoldersResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    for (Listener each : myListeners) {
      each.foldersResolved(projectWithChanges);
    }
  }

  void fireArtifactsDownloaded(@NotNull MavenProject project) {
    for (Listener each : myListeners) {
      each.artifactsDownloaded(project);
    }
  }

  private class UpdateContext {
    private final Map<MavenProject, MavenProjectChanges> updatedProjectsWithChanges = new ConcurrentHashMap<>();
    private final Set<MavenProject> deletedProjects = ConcurrentHashMap.newKeySet();

    public void update(MavenProject project, @NotNull MavenProjectChanges changes) {
      deletedProjects.remove(project);
      updatedProjectsWithChanges.compute(project, (__, previousChanges) ->
        previousChanges == null ? changes : MavenProjectChangesBuilder.merged(changes, previousChanges)
      );
    }

    public void deleted(MavenProject project) {
      updatedProjectsWithChanges.remove(project);
      deletedProjects.add(project);
    }

    public void fireUpdatedIfNecessary() {
      if (updatedProjectsWithChanges.isEmpty() && deletedProjects.isEmpty()) {
        return;
      }
      List<MavenProject> mavenProjects = deletedProjects.isEmpty()
                                         ? Collections.emptyList()
                                         : new ArrayList<>(deletedProjects);
      List<Pair<MavenProject, MavenProjectChanges>> updated = updatedProjectsWithChanges.isEmpty()
                                                              ? Collections.emptyList()
                                                              : mapToListWithPairs();
      fireProjectsUpdated(updated, mavenProjects);
    }

    @NotNull
    private List<Pair<MavenProject, MavenProjectChanges>> mapToListWithPairs() {
      ArrayList<Pair<MavenProject, MavenProjectChanges>> result = new ArrayList<>(updatedProjectsWithChanges.size());
      for (Map.Entry<MavenProject, MavenProjectChanges> entry : updatedProjectsWithChanges.entrySet()) {
        entry.getKey().getProblems(); // need for fill problem cache
        result.add(Pair.create(entry.getKey(), entry.getValue()));
      }
      return result;
    }

    public Collection<MavenProject> getDeletedProjects() {
      return deletedProjects;
    }
  }

  public abstract static class Visitor<Result> {
    private Result result;

    public boolean shouldVisit(MavenProject project) {
      return true;
    }

    public abstract void visit(MavenProject project);

    public void leave(MavenProject node) {
    }

    public void setResult(Result result) {
      this.result = result;
    }

    public Result getResult() {
      return result;
    }

    public boolean isDone() {
      return result != null;
    }
  }

  public abstract static class SimpleVisitor extends Visitor<Object> {
  }

  private static final class MavenProjectTimestamp {
    private final long myPomTimestamp;
    private final long myParentLastReadStamp;
    private final long myProfilesTimestamp;
    private final long myUserSettingsTimestamp;
    private final long myGlobalSettingsTimestamp;
    private final long myExplicitProfilesHashCode;
    private final long myJvmConfigTimestamp;
    private final long myMavenConfigTimestamp;

    private MavenProjectTimestamp(long pomTimestamp,
                                  long parentLastReadStamp,
                                  long profilesTimestamp,
                                  long userSettingsTimestamp,
                                  long globalSettingsTimestamp,
                                  long explicitProfilesHashCode,
                                  long jvmConfigTimestamp,
                                  long mavenConfigTimestamp) {
      myPomTimestamp = pomTimestamp;
      myParentLastReadStamp = parentLastReadStamp;
      myProfilesTimestamp = profilesTimestamp;
      myUserSettingsTimestamp = userSettingsTimestamp;
      myGlobalSettingsTimestamp = globalSettingsTimestamp;
      myExplicitProfilesHashCode = explicitProfilesHashCode;
      myJvmConfigTimestamp = jvmConfigTimestamp;
      myMavenConfigTimestamp = mavenConfigTimestamp;
    }

    public static MavenProjectTimestamp read(DataInputStream in) throws IOException {
      return new MavenProjectTimestamp(in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong());
    }

    public void write(DataOutputStream out) throws IOException {
      out.writeLong(myPomTimestamp);
      out.writeLong(myParentLastReadStamp);
      out.writeLong(myProfilesTimestamp);
      out.writeLong(myUserSettingsTimestamp);
      out.writeLong(myGlobalSettingsTimestamp);
      out.writeLong(myExplicitProfilesHashCode);
      out.writeLong(myJvmConfigTimestamp);
      out.writeLong(myMavenConfigTimestamp);
    }

    @Override
    public String toString() {
      return "(" + myPomTimestamp
             + ":" + myParentLastReadStamp
             + ":" + myProfilesTimestamp
             + ":" + myUserSettingsTimestamp
             + ":" + myGlobalSettingsTimestamp
             + ":" + myExplicitProfilesHashCode
             + ":" + myJvmConfigTimestamp
             + ":" + myMavenConfigTimestamp + ")";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MavenProjectTimestamp timestamp = (MavenProjectTimestamp)o;

      if (myPomTimestamp != timestamp.myPomTimestamp) return false;
      if (myParentLastReadStamp != timestamp.myParentLastReadStamp) return false;
      if (myProfilesTimestamp != timestamp.myProfilesTimestamp) return false;
      if (myUserSettingsTimestamp != timestamp.myUserSettingsTimestamp) return false;
      if (myGlobalSettingsTimestamp != timestamp.myGlobalSettingsTimestamp) return false;
      if (myExplicitProfilesHashCode != timestamp.myExplicitProfilesHashCode) return false;
      if (myJvmConfigTimestamp != timestamp.myJvmConfigTimestamp) return false;
      if (myMavenConfigTimestamp != timestamp.myMavenConfigTimestamp) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = 0;
      result = 31 * result + (int)(myPomTimestamp ^ (myPomTimestamp >>> 32));
      result = 31 * result + (int)(myParentLastReadStamp ^ (myParentLastReadStamp >>> 32));
      result = 31 * result + (int)(myProfilesTimestamp ^ (myProfilesTimestamp >>> 32));
      result = 31 * result + (int)(myUserSettingsTimestamp ^ (myUserSettingsTimestamp >>> 32));
      result = 31 * result + (int)(myGlobalSettingsTimestamp ^ (myGlobalSettingsTimestamp >>> 32));
      result = 31 * result + (int)(myExplicitProfilesHashCode ^ (myExplicitProfilesHashCode >>> 32));
      result = 31 * result + (int)(myJvmConfigTimestamp ^ (myJvmConfigTimestamp >>> 32));
      result = 31 * result + (int)(myMavenConfigTimestamp ^ (myMavenConfigTimestamp >>> 32));
      return result;
    }
  }

  public interface Listener extends EventListener {
    default void profilesChanged() {
    }

    default void projectsIgnoredStateChanged(@NotNull List<MavenProject> ignored,
                                             @NotNull List<MavenProject> unignored,
                                             boolean fromImport) {
    }

    default void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
    }

    default void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                 @Nullable NativeMavenProjectHolder nativeMavenProject) {
    }

    default void pluginsResolved(@NotNull MavenProject project) {
    }

    default void foldersResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    }

    default void artifactsDownloaded(@NotNull MavenProject project) {
    }
  }

  @ApiStatus.Internal
  public static final class MavenCoordinateHashCodeStrategy implements Hash.Strategy<MavenCoordinate> {
    @Override
    public int hashCode(MavenCoordinate object) {
      String artifactId = object == null ? null : object.getArtifactId();
      return artifactId == null ? 0 : artifactId.hashCode();
    }

    @Override
    public boolean equals(MavenCoordinate o1, MavenCoordinate o2) {
      if (o1 == o2) {
        return true;
      }
      if (o1 == null || o2 == null) {
        return false;
      }

      return Objects.equals(o1.getArtifactId(), o2.getArtifactId())
             && Objects.equals(o1.getVersion(), o2.getVersion())
             && Objects.equals(o1.getGroupId(), o2.getGroupId());
    }
  }

  public MavenProjectsTree getCopyForReimport() {
    return withReadLock(() -> {
      MavenProjectsTree result = new MavenProjectsTree(myProject);

      result.myExplicitProfiles = myExplicitProfiles;
      result.myRootProjects.addAll(myRootProjects);
      result.myIgnoredFilesPaths.addAll(myIgnoredFilesPaths);
      result.myIgnoredFilesPatterns.addAll(myIgnoredFilesPatterns);
      deepCopyInto(myAggregatorToModuleMapping, result.myAggregatorToModuleMapping);
      result.myIgnoredFilesPatternsCache = myIgnoredFilesPatternsCache;
      result.myManagedFilesPaths.addAll(myManagedFilesPaths);
      result.myMavenIdToProjectMapping.putAll(myMavenIdToProjectMapping);
      result.myModuleToAggregatorMapping.putAll(myModuleToAggregatorMapping);
      result.myVirtualFileToProjectMapping.putAll(myVirtualFileToProjectMapping);
      result.myTimestamps.putAll(myTimestamps);
      myWorkspaceMap.copyInto(result.myWorkspaceMap);
      return result;
    });
  }

  private static <K, V> Map<K, List<V>> deepCopyInto(Map<K, List<V>> from, Map<K, List<V>> to) {
    return deepCopyInto(from, to, Function.identity());
  }

  private static <K, V> Map<K, List<V>> deepCopyInto(Map<K, List<V>> from, Map<K, List<V>> to, Function<V, V> copyFunc) {
    for (Map.Entry<K, List<V>> entry : from.entrySet()) {
      List<V> newVal = new ArrayList<>(entry.getValue().size());
      for (V v : entry.getValue()) {
        newVal.add(copyFunc.apply(v));
      }
      to.put(entry.getKey(), newVal);
    }
    return to;
  }

  private <T extends Throwable> void withReadLock(@NotNull ThrowableRunnable<T> runnable) throws T {
    myStructureReadLock.lock();
    try {
      runnable.run();
    }
    finally {
      myStructureReadLock.unlock();
    }
  }

  @FunctionalInterface
  private interface ThrowableCallable<T extends Throwable, V> {
    V call() throws T;
  }


  private <T extends Throwable, V> V withReadLock(@NotNull ThrowableCallable<T, V> callable) throws T {
    myStructureReadLock.lock();
    try {
      return callable.call();
    }
    finally {
      myStructureReadLock.unlock();
    }
  }

  private <T extends Throwable> void withWriteLock(@NotNull ThrowableRunnable<T> runnable) throws T {
    myStructureWriteLock.lock();
    try {
      runnable.run();
    }
    finally {
      myStructureWriteLock.unlock();
    }
  }
}
