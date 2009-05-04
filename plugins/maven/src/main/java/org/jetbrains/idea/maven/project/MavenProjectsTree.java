package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.RuntimeInterruptedException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.*;
import java.util.*;

public class MavenProjectsTree {
  private static final String STORAGE_VERSION = MavenProjectsTree.class.getSimpleName() + ".2";

  private ReentrantWriterPreferenceReadWriteLock myLock = new ReentrantWriterPreferenceReadWriteLock();

  private List<String> myManagedFilesPaths = new ArrayList<String>();
  private List<String> myActiveProfiles = new ArrayList<String>();

  private List<MavenProject> myRootProjects = new ArrayList<MavenProject>();

  private Map<MavenProject, MavenProjectTimestamp> myTimestamps = new THashMap<MavenProject, MavenProjectTimestamp>();

  private Map<VirtualFile, MavenProject> myVirtualFileToProjectMapping = new THashMap<VirtualFile, MavenProject>();
  private Map<MavenId, MavenProject> myMavenIdToProjectMapping = new THashMap<MavenId, MavenProject>();
  private Map<MavenProject, List<MavenProject>> myAggregatorToModuleMapping = new THashMap<MavenProject, List<MavenProject>>();
  private Map<MavenProject, MavenProject> myModuleToAggregatorMapping = new THashMap<MavenProject, MavenProject>();

  private List<Listener> myListeners = ContainerUtil.createEmptyCOWList();

  private final MavenProjectReaderProjectLocator myProjectLocator = new MavenProjectReaderProjectLocator() {
    public VirtualFile findProjectFile(MavenId coordinates) {
      MavenProject project = findProject(coordinates);
      return project == null ? null : project.getFile();
    }
  };

  public static MavenProjectsTree read(File file) throws IOException {
    FileInputStream fs = new FileInputStream(file);
    DataInputStream in = new DataInputStream(fs);

    MavenProjectsTree result = new MavenProjectsTree();
    try {
      try {
        if (!STORAGE_VERSION.equals(in.readUTF())) return null;
        result.myManagedFilesPaths = readList(in);
        result.myActiveProfiles = readList(in);
        result.myRootProjects = readProjectsRecursively(in, result);
      }
      catch (Throwable e) {
        IOException ioException = new IOException();
        ioException.initCause(e);
        throw ioException;
      }
    }
    finally {
      in.close();
      fs.close();
    }
    return result;
  }

  private static List<String> readList(DataInputStream in) throws IOException {
    int count = in.readInt();
    List<String> result = new ArrayList<String>(count);
    while (count-- > 0) {
      result.add(in.readUTF());
    }
    return result;
  }

  private static List<MavenProject> readProjectsRecursively(DataInputStream in,
                                                            MavenProjectsTree tree) throws IOException {
    int count = in.readInt();
    List<MavenProject> result = new ArrayList<MavenProject>(count);
    while (count-- > 0) {
      MavenProject project = MavenProject.read(in);
      MavenProjectTimestamp timestamp = MavenProjectTimestamp.read(in);
      List<MavenProject> modules = readProjectsRecursively(in, tree);
      if (project != null) {
        result.add(project);
        tree.myTimestamps.put(project, timestamp);
        tree.myVirtualFileToProjectMapping.put(project.getFile(), project);
        tree.myMavenIdToProjectMapping.put(project.getMavenId(), project);
        tree.myAggregatorToModuleMapping.put(project, modules);
        for (MavenProject eachModule : modules) {
          tree.myModuleToAggregatorMapping.put(eachModule, project);
        }
      }
    }
    return result;
  }

  public void save(File file) throws IOException {
    readLock();
    try {
      file.getParentFile().mkdirs();
      FileOutputStream fs = new FileOutputStream(file);
      DataOutputStream out = new DataOutputStream(fs);
      try {
        out.writeUTF(STORAGE_VERSION);
        writeList(out, myManagedFilesPaths);
        writeList(out, myActiveProfiles);
        writeProjectsRecursively(out, myRootProjects);
      }
      finally {
        out.close();
        fs.close();
      }
    }
    finally {
      readUnlock();
    }
  }

  private static void writeList(DataOutputStream out, List<String> list) throws IOException {
    out.writeInt(list.size());
    for (String each : list) {
      out.writeUTF(each);
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

  public List<String> getManagedFilesPaths() {
    return myManagedFilesPaths;
  }

  public void resetManagedFilesPathsAndProfiles(List<String> paths, List<String> profiles) {
    myManagedFilesPaths = paths;
    setActiveProfiles(profiles);
  }

  @TestOnly
  public void resetManagedFilesAndProfiles(List<VirtualFile> files, List<String> profiles) {
    resetManagedFilesPathsAndProfiles(MavenUtil.collectPaths(files), profiles);
  }

  public void addManagedFilesWithProfiles(List<VirtualFile> files, List<String> profiles) {
    resetManagedFilesPathsAndProfiles(ContainerUtil.concat(myManagedFilesPaths, MavenUtil.collectPaths(files)),
                                      ContainerUtil.concat(myActiveProfiles, profiles));
  }

  public void removeManagedFiles(List<VirtualFile> files) {
    myManagedFilesPaths.removeAll(MavenUtil.collectPaths(files));
  }

  public List<VirtualFile> getExistingManagedFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String path : myManagedFilesPaths) {
      VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path);
      if (f != null) result.add(f);
    }
    return result;
  }

  public List<String> getActiveProfiles() {
    return myActiveProfiles;
  }

  public void setActiveProfiles(List<String> activeProfiles) {
    myActiveProfiles = new ArrayList<String>(activeProfiles);
    fireProfilesChanged(myActiveProfiles);
  }

  public void updateAll(MavenGeneralSettings generalSettings,
                        MavenProcess process) throws MavenProcessCanceledException {
    List<VirtualFile> managedFiles = getExistingManagedFiles();
    MavenProjectReader projectReader = new MavenProjectReader();
    update(projectReader, managedFiles,
           generalSettings,
           process,
           true);

    List<VirtualFile> obsoleteFiles = getRootProjectsFiles();
    obsoleteFiles.removeAll(managedFiles);
    delete(projectReader, obsoleteFiles, generalSettings, process);
  }

  public void update(Collection<VirtualFile> files,
                     MavenGeneralSettings generalSettings,
                     MavenProcess process) throws MavenProcessCanceledException {
    update(new MavenProjectReader(), files, generalSettings, process, false);
  }

  private void update(MavenProjectReader projectReader,
                      Collection<VirtualFile> files,
                      MavenGeneralSettings generalSettings,
                      MavenProcess process,
                      boolean recursive) throws MavenProcessCanceledException {
    if (files.isEmpty()) return;

    Map<VirtualFile, MavenProject> readProjects = new LinkedHashMap<VirtualFile, MavenProject>();
    Stack<MavenProject> updateStack = new Stack<MavenProject>();

    for (VirtualFile each : files) {
      MavenProject mavenProject = findProject(each);
      if (mavenProject == null) {
        doAdd(projectReader, each, readProjects, updateStack, generalSettings, process, recursive);
      }
      else {
        doUpdate(projectReader,
                 findAggregator(mavenProject),
                 mavenProject,
                 false,
                 readProjects,
                 updateStack,
                 generalSettings,
                 process,
                 recursive);
      }
    }
    fireProjectsRead(new ArrayList<MavenProject>(readProjects.values()));
  }

  private void doAdd(MavenProjectReader reader,
                     final VirtualFile f,
                     Map<VirtualFile, MavenProject> readProjects,
                     Stack<MavenProject> updateStack,
                     MavenGeneralSettings generalSettings,
                     MavenProcess process,
                     boolean recursuve) throws MavenProcessCanceledException {
    MavenProject newMavenProject = new MavenProject(f);

    MavenProject intendedAggregator = visit(new Visitor<MavenProject>() {
      public void visit(MavenProject node) {
        if (node.getExistingModuleFiles().contains(f)) {
          setResult(node);
        }
      }
    });

    doUpdate(reader,
             intendedAggregator,
             newMavenProject,
             true,
             readProjects,
             updateStack,
             generalSettings,
             process,
             recursuve);
  }

  private void doUpdate(MavenProjectReader reader,
                        MavenProject aggregator,
                        MavenProject mavenProject,
                        boolean isNew,
                        Map<VirtualFile, MavenProject> readProjects,
                        Stack<MavenProject> updateStack,
                        MavenGeneralSettings generalSettings,
                        MavenProcess process,
                        boolean recursive) throws MavenProcessCanceledException {
    process.checkCanceled();

    if (updateStack.contains(mavenProject)) {
      MavenLog.LOG.info("Recursion detected in " + mavenProject.getFile());
      return;
    }
    updateStack.push(mavenProject);

    process.setText(ProjectBundle.message("maven.reading.pom", mavenProject.getPath()));
    process.setText2("");

    List<MavenProject> prevModules = getModules(mavenProject);
    Set<MavenProject> prevInheritors = isNew
                                       ? new THashSet<MavenProject>()
                                       : findInheritors(mavenProject);

    // todo hook for IDEADEV-31568
    if (isNew) {
      VirtualFile f = mavenProject.getFile();
      if (readProjects.containsKey(f)) {
        assert false : "new file alread read " + f;
      }
    }

    MavenProjectTimestamp timestamp = calculateTimestamp(mavenProject, myActiveProfiles, generalSettings);
    boolean isChanged = !timestamp.equals(myTimestamps.get(mavenProject));

    if (isChanged) {
      writeLock();
      try {
        if (!isNew) myMavenIdToProjectMapping.remove(mavenProject.getMavenId());
      }
      finally {
        writeUnlock();
      }
      mavenProject.read(generalSettings, myActiveProfiles, reader, myProjectLocator);
      readProjects.put(mavenProject.getFile(), mavenProject);

      writeLock();
      try {
        myVirtualFileToProjectMapping.put(mavenProject.getFile(), mavenProject);
        myMavenIdToProjectMapping.put(mavenProject.getMavenId(), mavenProject);
      }
      finally {
        writeUnlock();
      }

      timestamp = calculateTimestamp(mavenProject, myActiveProfiles, generalSettings);
      myTimestamps.put(mavenProject, timestamp);
      resolveIntermoduleDependencies(mavenProject);
    }

    boolean reconnected = reconnect(aggregator, mavenProject);
    if (isChanged) {
      fireProjectRead(mavenProject);
    }
    else if (reconnected) {
      fireAggregatorChanged(mavenProject);
    }

    List<VirtualFile> existingModuleFiles = mavenProject.getExistingModuleFiles();
    List<MavenProject> modulesToRemove = new ArrayList<MavenProject>();
    List<MavenProject> modulesToBecomeRoots = new ArrayList<MavenProject>();

    for (MavenProject each : prevModules) {
      VirtualFile moduleFile = each.getFile();
      if (!existingModuleFiles.contains(moduleFile)) {
        if (isManagedFile(moduleFile)) {
          modulesToBecomeRoots.add(each);
        }
        else {
          modulesToRemove.add(each);
        }
      }
    }
    for (MavenProject each : modulesToRemove) {
      removeModule(mavenProject, each);
      List<MavenProject> removedProjects = new ArrayList<MavenProject>();
      doRemove(mavenProject, each, removedProjects);
      prevInheritors.removeAll(removedProjects);
    }

    for (MavenProject each : modulesToBecomeRoots) {
      if (reconnect(null, each)) fireAggregatorChanged(each);
    }

    for (VirtualFile each : existingModuleFiles) {
      MavenProject child = findProject(each);
      boolean isNewModule = child == null;
      if (isNewModule) {
        child = new MavenProject(each);
      }
      else {
        MavenProject currentAggregator = findAggregator(child);
        if (currentAggregator != null && currentAggregator != mavenProject) {
          MavenLog.LOG.info("Module " + each + " is already included into " + mavenProject.getFile());
          continue;
        }
      }

      if (isChanged || isNewModule || recursive) {
        doUpdate(reader,
                 mavenProject,
                 child,
                 isNewModule,
                 readProjects,
                 updateStack,
                 generalSettings,
                 process,
                 recursive);
      }
      else {
        if (reconnect(mavenProject, child)) {
          fireAggregatorChanged(child);
        }
      }
    }

    Set<MavenProject> allInheritors = findInheritors(mavenProject);
    allInheritors.addAll(prevInheritors);
    for (MavenProject each : allInheritors) {
      doUpdate(reader,
               findAggregator(each),
               each,
               false,
               readProjects,
               updateStack,
               generalSettings,
               process,
               recursive);
    }

    updateStack.pop();
  }

  private void resolveIntermoduleDependencies(MavenProject mavenProject) {
    // todo!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // todo!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // todo!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // todo!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    for (MavenArtifact eachDependency : mavenProject.getDependencies()) {
      MavenProject dependencyProject = findProject(eachDependency.getMavenId());
      if (dependencyProject != null) {
        eachDependency.setResolved(new File(dependencyProject.getPath()));
      }
    }
  }

  private MavenProjectTimestamp calculateTimestamp(final MavenProject mavenProject,
                                                   final List<String> activeProfiles,
                                                   final MavenGeneralSettings generalSettings) {
    long pomTimestamp = getFileTimestamp(mavenProject.getFile());
    MavenProject parent = findParent(mavenProject);
    long parentLastReadStamp = parent == null ? -1 : parent.getLastReadStamp();
    VirtualFile profilesXmlFile = mavenProject.getDirectoryFile().findChild(MavenConstants.PROFILES_XML);
    long profilesTimestamp = getFileTimestamp(profilesXmlFile);

    File userSettings = MavenEmbedderFactory.resolveUserSettingsFile(generalSettings.getMavenSettingsFile());
    File globalSettings = MavenEmbedderFactory.resolveGlobalSettingsFile(generalSettings.getMavenHome());
    long userSettingsTimestamp = getFileTimestamp(userSettings);
    long globalSettingsTimestamp = getFileTimestamp(globalSettings);

    int profilesHashCode = new THashSet<String>(activeProfiles).hashCode();

    return new MavenProjectTimestamp(pomTimestamp,
                                     parentLastReadStamp,
                                     profilesTimestamp,
                                     userSettingsTimestamp,
                                     globalSettingsTimestamp,
                                     profilesHashCode);
  }

  private long getFileTimestamp(File file) {
    return getFileTimestamp(file == null ? null : LocalFileSystem.getInstance().findFileByIoFile(file));
  }

  private long getFileTimestamp(VirtualFile file) {
    if (file == null) return -1;
    return file.getModificationStamp();
  }

  public boolean isManagedFile(VirtualFile moduleFile) {
    return isManagedFile(moduleFile.getPath());
  }

  public boolean isManagedFile(String path) {
    for (String each : myManagedFilesPaths) {
      if (FileUtil.pathsEqual(each, path)) return true;
    }
    return false;
  }

  public boolean isPotentialProject(String path) {
    if (isManagedFile(path)) return true;

    for (MavenProject each : getProjects()) {
      if (FileUtil.pathsEqual(path, each.getPath())) return true;
      if (each.getModulePaths().contains(path)) return true;
    }
    return false;
  }

  private boolean reconnect(MavenProject newAggregator, MavenProject project) {
    MavenProject prevAggregator = findAggregator(project);

    writeLock();
    try {
      if (prevAggregator != null) {
        removeModule(prevAggregator, project);
      }
      else {
        myRootProjects.remove(project);
      }

      if (newAggregator != null) {
        addModule(newAggregator, project);
      }
      else {
        myRootProjects.add(project);
      }
    }
    finally {
      writeUnlock();
    }

    return prevAggregator != newAggregator;
  }

  public void delete(List<VirtualFile> files,
                     MavenGeneralSettings generalSettings,
                     MavenProcess process) throws MavenProcessCanceledException {
    delete(new MavenProjectReader(), files, generalSettings, process);
  }

  private void delete(MavenProjectReader projectReader,
                      List<VirtualFile> files,
                      MavenGeneralSettings generalSettings,
                      MavenProcess process) throws MavenProcessCanceledException {
    if (files.isEmpty()) return;

    List<MavenProject> projectsToUpdate = new ArrayList<MavenProject>();
    List<MavenProject> removedProjects = new ArrayList<MavenProject>();

    for (VirtualFile each : files) {
      process.checkCanceled();

      MavenProject mavenProject = findProject(each);
      if (mavenProject == null) return;

      projectsToUpdate.addAll(findInheritors(mavenProject));
      doRemove(findAggregator(mavenProject), mavenProject, removedProjects);
    }

    projectsToUpdate.removeAll(removedProjects);

    List<VirtualFile> filesToUpdate = new ArrayList<VirtualFile>();
    for (MavenProject each : projectsToUpdate) {
      filesToUpdate.add(each.getFile());
    }

    update(projectReader, filesToUpdate, generalSettings, process, false);
  }

  private void doRemove(MavenProject aggregator, MavenProject project, List<MavenProject> removedProjects)
    throws MavenProcessCanceledException {
    for (MavenProject each : getModules(project)) {
      doRemove(project, each, removedProjects);
    }

    writeLock();
    try {
      if (aggregator != null) {
        removeModule(aggregator, project);
      }
      else {
        myRootProjects.remove(project);
      }
      myVirtualFileToProjectMapping.remove(project.getFile());
      myMavenIdToProjectMapping.remove(project.getMavenId());
      myAggregatorToModuleMapping.remove(project);
      myModuleToAggregatorMapping.remove(project);
    }
    finally {
      writeUnlock();
    }

    removedProjects.add(project);
    fireRemoved(project);
  }

  public List<MavenProject> getRootProjects() {
    readLock();
    try {
      return new ArrayList<MavenProject>(myRootProjects);
    }
    finally {
      readUnlock();
    }
  }

  public List<VirtualFile> getRootProjectsFiles() {
    return ContainerUtil.map(getRootProjects(), new Function<MavenProject, VirtualFile>() {
      public VirtualFile fun(MavenProject each) {
        return each.getFile();
      }
    });
  }

  public List<MavenProject> getProjects() {
    readLock();
    try {
      return new ArrayList<MavenProject>(myVirtualFileToProjectMapping.values());
    }
    finally {
      readUnlock();
    }
  }

  public List<VirtualFile> getProjectsFiles() {
    readLock();
    try {
      return new ArrayList<VirtualFile>(myVirtualFileToProjectMapping.keySet());
    }
    finally {
      readUnlock();
    }
  }

  public MavenProject findProject(VirtualFile f) {
    readLock();
    try {
      return myVirtualFileToProjectMapping.get(f);
    }
    finally {
      readUnlock();
    }
  }

  public MavenProject findProject(MavenId id) {
    readLock();
    try {
      return myMavenIdToProjectMapping.get(id);
    }
    finally {
      readUnlock();
    }
  }

  public MavenProject findProject(Artifact artifact) {
    return findProject(new MavenId(artifact));
  }

  public MavenProject findAggregator(MavenProject project) {
    readLock();
    try {
      return myModuleToAggregatorMapping.get(project);
    }
    finally {
      readUnlock();
    }
  }

  public boolean isModuleOf(MavenProject aggregator, MavenProject module) {
    return aggregator.equals(findAggregator(module));
  }

  public List<MavenProject> getModules(MavenProject aggregator) {
    readLock();
    try {
      List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
      return modules == null
             ? Collections.<MavenProject>emptyList()
             : new ArrayList<MavenProject>(modules);
    }
    finally {
      readUnlock();
    }
  }

  private void addModule(MavenProject aggregator, MavenProject module) {
    writeLock();
    try {
      List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
      if (modules == null) {
        modules = new ArrayList<MavenProject>();
        myAggregatorToModuleMapping.put(aggregator, modules);
      }
      modules.add(module);

      myModuleToAggregatorMapping.put(module, aggregator);
    }
    finally {
      writeUnlock();
    }
  }

  private void removeModule(MavenProject aggregator, MavenProject module) {
    writeLock();
    try {
      List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
      if (modules == null) return;
      modules.remove(module);
      myModuleToAggregatorMapping.remove(module);
    }
    finally {
      writeUnlock();
    }
  }

  private MavenProject findParent(MavenProject project) {
    return findProject(project.getParentId());
  }

  private Set<MavenProject> findInheritors(MavenProject project) {
    Set<MavenProject> result = new THashSet<MavenProject>();
    MavenId id = project.getMavenId();

    for (MavenProject each : getProjects()) {
      if (each == project) continue;
      if (id.equals(each.getParentId())) result.add(each);
    }
    return result;
  }

  public void resolve(boolean quickResolve,
                      MavenGeneralSettings generalSettings,
                      MavenProject mavenProject,
                      MavenEmbeddersManager embeddersManager,
                      MavenConsole console,
                      MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(quickResolve, this, console, process);

    try {
      process.checkCanceled();
      process.setText(ProjectBundle.message("maven.resolving.pom", FileUtil.toSystemDependentName(mavenProject.getPath())));
      process.setText2("");
      org.apache.maven.project.MavenProject nativeProject = mavenProject.resolve(generalSettings,
                                                                                 embedder,
                                                                                 new MavenProjectReader(),
                                                                                 myProjectLocator,
                                                                                 process);
      fireProjectResolved(quickResolve, mavenProject, nativeProject);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public void resolvePlugins(MavenProject mavenProject,
                             org.apache.maven.project.MavenProject nativeMavenProject,
                             MavenEmbeddersManager embeddersManager,
                             MavenConsole console,
                             MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(console, process);
    try {
      for (MavenPlugin each : mavenProject.getPlugins()) {
        process.checkCanceled();
        process.setText(ProjectBundle.message("maven.downloading.artifact", each + " plugin"));
        embedder.resolvePlugin(each, nativeMavenProject, process);
      }
      firePluginsResolved(mavenProject);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public void resolveFolders(MavenProject mavenProject,
                             MavenEmbeddersManager embeddersManager,
                             MavenImportingSettings importingSettings,
                             MavenConsole console,
                             MavenProcess process) throws MavenProcessCanceledException {
    if (mavenProject.isAggregator()) return;

    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForStrictResolve(this, console, process);

    try {
      process.checkCanceled();
      process.setText(ProjectBundle.message("maven.updating.folders.pom", FileUtil.toSystemDependentName(mavenProject.getPath())));
      process.setText2("");
      mavenProject.resolveFolders(embedder, importingSettings, new MavenProjectReader(), console, process);
      fireFoldersResolved(mavenProject);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public void downloadArtifacts(MavenProject mavenProject,
                                MavenDownloadingSettings downloadingSettings,
                                MavenEmbeddersManager embeddersManager,
                                MavenConsole console,
                                MavenProcess process) throws MavenProcessCanceledException {
    if (mavenProject.isAggregator()) return;

    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(console, process);

    try {
      MavenArtifactDownloader.download(this, Collections.singletonList(mavenProject), downloadingSettings, true, embedder, process);
      fireArtifactsDownloaded(mavenProject);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public MavenArtifact downloadArtifact(MavenProject mavenProject,
                                        MavenId id,
                                        MavenEmbeddersManager embeddersManager,
                                        MavenConsole console,
                                        MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(console, process);

    try {
      Artifact artifact = embedder.createArtifact(id.groupId,
                                                  id.artifactId,
                                                  id.version,
                                                  MavenConstants.TYPE_JAR,
                                                  null);
      artifact.setScope(Artifact.SCOPE_COMPILE);
      embedder.resolve(artifact, mavenProject.getRemoteRepositories(), process);
      return new MavenArtifact(artifact);
    }
    finally {
      embeddersManager.release(embedder);
    }
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

  private void writeLock() {
    try {
      myLock.writeLock().acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  private void writeUnlock() {
    myLock.writeLock().release();
  }

  private void readLock() {
    try {
      myLock.readLock().acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  private void readUnlock() {
    myLock.readLock().release();
  }

  public void addListener(Listener l) {
    myListeners.add(l);
  }

  private void fireProfilesChanged(List<String> profiles) {
    for (Listener each : myListeners) {
      each.profilesChanged(profiles);
    }
  }

  private void fireProjectsRead(List<MavenProject> projects) {
    for (Listener each : myListeners) {
      each.projectsRead(projects);
    }
  }

  private void fireProjectRead(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectRead(project);
    }
  }

  private void fireAggregatorChanged(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectAggregatorChanged(project);
    }
  }

  private void fireRemoved(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectRemoved(project);
    }
  }

  private void fireProjectResolved(boolean quickResolve, MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
    for (Listener each : myListeners) {
      each.projectResolved(quickResolve, project, nativeMavenProject);
    }
  }

  private void firePluginsResolved(MavenProject project) {
    for (Listener each : myListeners) {
      each.pluginsResolved(project);
    }
  }

  private void fireFoldersResolved(MavenProject project) {
    for (Listener each : myListeners) {
      each.foldersResolved(project);
    }
  }

  private void fireArtifactsDownloaded(MavenProject project) {
    for (Listener each : myListeners) {
      each.artifactsDownloaded(project);
    }
  }

  public static abstract class Visitor<Result> {
    private Result result;

    public boolean shouldVisit(MavenProject node) {
      return node.isIncluded();
    }

    public abstract void visit(MavenProject node);

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

  public static abstract class SimpleVisitor extends Visitor<Object> {
  }

  private static class MavenProjectTimestamp {
    private final long myPomTimestamp;
    private final long myParentLastReadStamp;
    private final long myProfilesTimestamp;
    private final long myUserSettingsTimestamp;
    private final long myGlobalSettingsTimestamp;
    private final long myActiveProfilesHashCode;

    private MavenProjectTimestamp(long pomTimestamp,
                                  long parentLastReadStamp,
                                  long profilesTimestamp,
                                  long userSettingsTimestamp,
                                  long globalSettingsTimestamp,
                                  long activeProfilesHashCode) {
      myPomTimestamp = pomTimestamp;
      myParentLastReadStamp = parentLastReadStamp;
      myProfilesTimestamp = profilesTimestamp;
      myUserSettingsTimestamp = userSettingsTimestamp;
      myGlobalSettingsTimestamp = globalSettingsTimestamp;
      myActiveProfilesHashCode = activeProfilesHashCode;
    }

    public static MavenProjectTimestamp read(DataInputStream in) throws IOException {
      return new MavenProjectTimestamp(in.readLong(),
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
      out.writeLong(myActiveProfilesHashCode);
    }

    @Override
    public String toString() {
      return "(" + myPomTimestamp
             + ":" + myParentLastReadStamp
             + ":" + myProfilesTimestamp
             + ":" + myUserSettingsTimestamp
             + ":" + myGlobalSettingsTimestamp
             + ":" + myActiveProfilesHashCode + ")";
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
      if (myActiveProfilesHashCode != timestamp.myActiveProfilesHashCode) return false;

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
      result = 31 * result + (int)(myActiveProfilesHashCode ^ (myActiveProfilesHashCode >>> 32));
      return result;
    }
  }

  public interface Listener extends EventListener {
    void profilesChanged(List<String> profiles);

    void projectsRead(List<MavenProject> projects);

    void projectRead(MavenProject project);

    void projectAggregatorChanged(MavenProject project);

    void projectRemoved(MavenProject project);

    void projectResolved(boolean quickResolve, MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject);

    void pluginsResolved(MavenProject project);

    void foldersResolved(MavenProject project);

    void artifactsDownloaded(MavenProject project);
  }

  public static class ListenerAdapter implements Listener {
    public void profilesChanged(List<String> profiles) {
    }

    public void projectsRead(List<MavenProject> projects) {
    }

    public void projectRead(MavenProject project) {
    }

    public void projectAggregatorChanged(MavenProject project) {
    }

    public void projectRemoved(MavenProject project) {
    }

    public void projectResolved(boolean quickResolve, MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
    }

    public void pluginsResolved(MavenProject project) {
    }

    public void foldersResolved(MavenProject project) {
    }

    public void artifactsDownloaded(MavenProject project) {
    }
  }
}
