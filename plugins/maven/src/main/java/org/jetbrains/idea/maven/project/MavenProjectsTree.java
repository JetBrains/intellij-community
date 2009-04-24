package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.RuntimeInterruptedException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import com.intellij.util.containers.ContainerUtil;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
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
  private HashMap<MavenId, MavenProject> myMavenIdToProject = new HashMap<MavenId, MavenProject>();
  private Map<MavenProject, List<MavenProject>> myModuleMapping = new HashMap<MavenProject, List<MavenProject>>();

  private Map<MavenProject, MavenProjectTimestamp> myTimestamps = new HashMap<MavenProject, MavenProjectTimestamp>();

  private List<Listener> myListeners = new ArrayList<Listener>();

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
        result.myRootProjects = readProjectsRecursively(in,
                                                        result.myMavenIdToProject,
                                                        result.myModuleMapping,
                                                        result.myTimestamps);
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
                                                            HashMap<MavenId, MavenProject> mavenIdToProject,
                                                            Map<MavenProject, List<MavenProject>> moduleMapping,
                                                            Map<MavenProject, MavenProjectTimestamp> timestamps) throws IOException {
    int count = in.readInt();
    List<MavenProject> result = new ArrayList<MavenProject>(count);
    while (count-- > 0) {
      MavenProject project = MavenProject.read(in);
      MavenProjectTimestamp timestamp = MavenProjectTimestamp.read(in);
      List<MavenProject> modules = readProjectsRecursively(in, mavenIdToProject, moduleMapping, timestamps);
      // todo what otherwise?
      if (project != null) {
        result.add(project);
        mavenIdToProject.put(project.getMavenId(), project);
        moduleMapping.put(project, modules);
        timestamps.put(project, timestamp);
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

  public void updateAll(boolean quickUpdate,
                        MavenEmbeddersManager embeddersManager,
                        MavenGeneralSettings generalSettings,
                        MavenConsole console,
                        MavenProcess process) throws MavenProcessCanceledException {
    List<VirtualFile> managedFiles = getExistingManagedFiles();
    update(managedFiles,
           quickUpdate,
           embeddersManager,
           generalSettings,
           console,
           process,
           true);

    List<VirtualFile> obsoleteFiles = getRootProjectsFiles();
    obsoleteFiles.removeAll(managedFiles);
    delete(obsoleteFiles, quickUpdate, embeddersManager, generalSettings, console, process);
  }

  public void update(Collection<VirtualFile> files,
                     boolean quickUpdate,
                     MavenEmbeddersManager embeddersManager,
                     MavenGeneralSettings generalSettings,
                     MavenConsole console,
                     MavenProcess process) throws MavenProcessCanceledException {
    update(files, quickUpdate, embeddersManager, generalSettings, console, process, false);
  }

  private void update(Collection<VirtualFile> files,
                      boolean quickUpdate,
                      MavenEmbeddersManager embeddersManager,
                      MavenGeneralSettings generalSettings,
                      MavenConsole console,
                      MavenProcess process,
                      boolean recursive) throws MavenProcessCanceledException {
    if (files.isEmpty()) return;

    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForRead(this, console, process);

    Map<VirtualFile, MavenProject> readProjects = new LinkedHashMap<VirtualFile, MavenProject>();
    try {
      Stack<MavenProject> updateStack = new Stack<MavenProject>();

      for (VirtualFile each : files) {
        MavenProject mavenProject = findProject(each);
        if (mavenProject == null) {
          doAdd(each, quickUpdate, embedder, readProjects, updateStack, generalSettings, process, recursive);
        }
        else {
          doUpdate(findAggregator(mavenProject),
                   mavenProject,
                   quickUpdate,
                   embedder,
                   false,
                   readProjects,
                   updateStack,
                   generalSettings,
                   process,
                   recursive);
        }
      }
    }
    finally {
      embeddersManager.release(embedder);
    }
    if (quickUpdate) {
      fireProjectsRead(new ArrayList<MavenProject>(readProjects.values()));
    }
  }

  private void doAdd(final VirtualFile f,
                     boolean quickUpdate,
                     MavenEmbedderWrapper embedder,
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

    doUpdate(intendedAggregator,
             newMavenProject,
             quickUpdate,
             embedder,
             true,
             readProjects,
             updateStack,
             generalSettings,
             process,
             recursuve);
  }

  private void doUpdate(MavenProject aggregator,
                        MavenProject mavenProject,
                        boolean quickUpdate,
                        MavenEmbedderWrapper embedder,
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
                                       ? new HashSet<MavenProject>()
                                       : findInheritors(mavenProject);

    // todo hook for IDEADEV-31568
    if (isNew) {
      VirtualFile f = mavenProject.getFile();
      if (readProjects.containsKey(f)) {
        assert false : "new file alread read " + f;
      }
    }

    MavenProjectTimestamp timestamp = calculateTimestamp(mavenProject, quickUpdate, myActiveProfiles, generalSettings);
    boolean isChanged = !timestamp.equals(myTimestamps.get(mavenProject));

    org.apache.maven.project.MavenProject nativeMavenProject = null;
    if (isChanged) {
      writeLock();
      try {
        if (!isNew) myMavenIdToProject.remove(mavenProject.getMavenId());
      }
      finally {
        writeUnlock();
      }
      if (quickUpdate) {
        mavenProject.readQuickly(generalSettings, myActiveProfiles, myProjectLocator);
      }
      else {
        nativeMavenProject = mavenProject.read(generalSettings, embedder, myActiveProfiles, myProjectLocator, process);
      }
      readProjects.put(mavenProject.getFile(), mavenProject);

      writeLock();
      try {
        myMavenIdToProject.put(mavenProject.getMavenId(), mavenProject);
      }
      finally {
        writeUnlock();
      }

      timestamp = calculateTimestamp(mavenProject, quickUpdate, myActiveProfiles, generalSettings);
      myTimestamps.put(mavenProject, timestamp);
      resolveIntermoduleDependencies();
    }

    boolean reconnected = reconnect(aggregator, mavenProject);
    if (isChanged) {
      if (!quickUpdate) {
        fireRead(mavenProject, nativeMavenProject);
      }
    }
    else if (reconnected) {
      fireAggregatorChanged(mavenProject);
    }

    List<VirtualFile> existingModuleFiles = mavenProject.getExistingModuleFiles();
    List<MavenProject> modulesToRemove = new ArrayList<MavenProject>();
    List<MavenProject> modulesToReconnect = new ArrayList<MavenProject>();

    for (MavenProject each : prevModules) {
      VirtualFile moduleFile = each.getFile();
      if (!existingModuleFiles.contains(moduleFile)) {
        if (isManagedFile(moduleFile)) {
          modulesToReconnect.add(each);
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

    for (MavenProject each : modulesToReconnect) {
      if (reconnect(null, each)) fireRead(each, nativeMavenProject);
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
        doUpdate(
          mavenProject,
          child,
          quickUpdate,
          embedder,
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
      doUpdate(
        findAggregator(each),
        each,
        quickUpdate,
        embedder,
        false,
        readProjects,
        updateStack,
        generalSettings,
        process,
        recursive);
    }

    updateStack.pop();
  }

  private MavenProjectTimestamp calculateTimestamp(final MavenProject mavenProject,
                                                   final boolean quickRead,
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

    int profilesHashCode = new HashSet<String>(activeProfiles).hashCode();

    return new MavenProjectTimestamp(quickRead,
                                     pomTimestamp,
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

  private void resolveIntermoduleDependencies() {
    for (MavenProject eachProject : getProjects()) {
      for (MavenArtifact eachDependency : eachProject.getDependencies()) {
        MavenProject project = myMavenIdToProject.get(eachDependency.getMavenId());
        if (project != null) {
          eachDependency.setResolved(new File(project.getPath()));
        }
      }
    }
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
                     boolean quickUpdate,
                     MavenEmbeddersManager embeddersManager,
                     MavenGeneralSettings generalSettings,
                     MavenConsole console,
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

    update(filesToUpdate, quickUpdate, embeddersManager, generalSettings, console, process, false);
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
      myMavenIdToProject.remove(project.getMavenId());
      myModuleMapping.remove(project);
    }
    finally {
      writeUnlock();
    }

    removedProjects.add(project);
    fireRemoved(project);
  }

  private MavenProject findAggregator(final MavenProject project) {
    return visit(new Visitor<MavenProject>() {
      public void visit(MavenProject each) {
        if (getModules(each).contains(project)) {
          setResult(each);
        }
      }
    });
  }

  private MavenProject findParent(final MavenProject project) {
    return findProject(project.getParentId());
  }

  private Set<MavenProject> findInheritors(final MavenProject project) {
    final Set<MavenProject> result = new HashSet<MavenProject>();
    final MavenId id = project.getMavenId();

    visit(new SimpleVisitor() {
      public void visit(MavenProject each) {
        if (each == project) return;
        if (id.equals(each.getParentId())) result.add(each);
      }
    });
    return result;
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
    final List<MavenProject> result = new ArrayList<MavenProject>();
    visit(new SimpleVisitor() {
      public void visit(MavenProject each) {
        result.add(each);
      }
    });
    return result;
  }

  public MavenProject findProject(final VirtualFile f) {
    return visit(new Visitor<MavenProject>() {
      public void visit(MavenProject each) {
        if (each.getFile() == f) {
          setResult(each);
        }
      }
    });
  }

  public MavenProject findProject(MavenId id) {
    readLock();
    try {
      return myMavenIdToProject.get(id);
    }
    finally {
      readUnlock();
    }
  }

  public MavenProject findProject(Artifact artifact) {
    return findProject(new MavenId(artifact));
  }

  public List<VirtualFile> getFiles() {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    visit(new SimpleVisitor() {
      public void visit(MavenProject each) {
        result.add(each.getFile());
      }
    });
    return result;
  }

  public boolean isModuleOf(MavenProject aggregator, MavenProject module) {
    return getModules(aggregator).contains(module);
  }

  public List<MavenProject> getModules(MavenProject aggregator) {
    readLock();
    try {
      List<MavenProject> modules = myModuleMapping.get(aggregator);
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
      List<MavenProject> modules = myModuleMapping.get(aggregator);
      if (modules == null) {
        modules = new ArrayList<MavenProject>();
        myModuleMapping.put(aggregator, modules);
      }
      modules.add(module);
    }
    finally {
      writeUnlock();
    }
  }

  private void removeModule(MavenProject aggregator, MavenProject module) {
    writeLock();
    try {
      List<MavenProject> modules = myModuleMapping.get(aggregator);
      if (modules == null) return;
      modules.remove(module);
    }
    finally {
      writeUnlock();
    }
  }

  public void resolve(MavenGeneralSettings generalSettings,
                      MavenProject mavenProject,
                      MavenEmbeddersManager embeddersManager,
                      MavenConsole console,
                      MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(this, console, process);

    try {
      process.checkCanceled();
      process.setText(ProjectBundle.message("maven.resolving.pom", FileUtil.toSystemDependentName(mavenProject.getPath())));
      process.setText2("");
      mavenProject.resolve(generalSettings, embedder, myProjectLocator, process);
      fireResolved(mavenProject);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public void downloadPlugins(MavenProject project,
                              org.apache.maven.project.MavenProject nativeMavenProject,
                              MavenEmbeddersManager embeddersManager,
                              MavenConsole console,
                              MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(console, process);
    try {
      for (MavenPlugin each : project.getPlugins()) {
        process.checkCanceled();
        process.setText(ProjectBundle.message("maven.downloading.artifact", each + " plugin"));
        embedder.resolvePlugin(each, nativeMavenProject, process);
      }
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public void generateSources(MavenProject project,
                              MavenEmbeddersManager embeddersManager,
                              MavenImportingSettings importingSettings,
                              MavenConsole console,
                              MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(this, true, console, process);

    try {
      process.checkCanceled();
      process.setText(ProjectBundle.message("maven.updating.folders.pom", FileUtil.toSystemDependentName(project.getPath())));
      process.setText2("");
      project.generateSources(embedder, importingSettings, console, process);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public void downloadArtifacts(MavenProject project,
                                MavenDownloadingSettings downloadingSettings,
                                MavenEmbeddersManager embeddersManager,
                                MavenConsole console,
                                MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(console, process);

    try {
      MavenArtifactDownloader.download(this, Collections.singletonList(project), downloadingSettings, true, embedder, process);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public MavenDomDependency addDependency(Project project,
                                          MavenProject mavenProject,
                                          MavenArtifact artifact) throws MavenProcessCanceledException {
    return mavenProject.addDependency(project, artifact);
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
      each.projectsReadQuickly(projects);
    }
  }

  private void fireRead(MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
    for (Listener each : myListeners) {
      each.projectRead(project, nativeMavenProject);
    }
  }

  private void fireAggregatorChanged(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectAggregatorChanged(project);
    }
  }

  private void fireResolved(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectResolved(project);
    }
  }

  private void fireRemoved(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectRemoved(project);
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
    private final boolean myQuickRead;
    private final long myPomTimestamp;
    private final long myParentLastReadStamp;
    private final long myProfilesTimestamp;
    private final long myUserSettingsTimestamp;
    private final long myGlobalSettingsTimestamp;
    private final long myActiveProfilesHashCode;

    private MavenProjectTimestamp(boolean quickRead,
                                  long pomTimestamp,
                                  long parentLastReadStamp,
                                  long profilesTimestamp,
                                  long userSettingsTimestamp,
                                  long globalSettingsTimestamp,
                                  long activeProfilesHashCode) {
      myQuickRead = quickRead;
      myPomTimestamp = pomTimestamp;
      myParentLastReadStamp = parentLastReadStamp;
      myProfilesTimestamp = profilesTimestamp;
      myUserSettingsTimestamp = userSettingsTimestamp;
      myGlobalSettingsTimestamp = globalSettingsTimestamp;
      myActiveProfilesHashCode = activeProfilesHashCode;
    }

    public static MavenProjectTimestamp read(DataInputStream in) throws IOException {
      return new MavenProjectTimestamp(in.readBoolean(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong(),
                                       in.readLong());
    }

    public void write(DataOutputStream out) throws IOException {
      out.writeBoolean(myQuickRead);
      out.writeLong(myPomTimestamp);
      out.writeLong(myParentLastReadStamp);
      out.writeLong(myProfilesTimestamp);
      out.writeLong(myUserSettingsTimestamp);
      out.writeLong(myGlobalSettingsTimestamp);
      out.writeLong(myActiveProfilesHashCode);
    }

    @Override
    public String toString() {
      return "(" + myQuickRead
             + ":" + myPomTimestamp
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

      if (myQuickRead != timestamp.myQuickRead) return false;
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
      int result = (myQuickRead ? 1 : 0);
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

    void projectsReadQuickly(List<MavenProject> projects);

    void projectRead(MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject);

    void projectAggregatorChanged(MavenProject project);

    void projectResolved(MavenProject project);

    void projectRemoved(MavenProject project);
  }

  public static class ListenerAdapter implements Listener {
    public void profilesChanged(List<String> profiles) {
    }

    public void projectsReadQuickly(List<MavenProject> projects) {
    }

    public void projectRead(MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
    }

    public void projectAggregatorChanged(MavenProject project) {
    }

    public void projectResolved(MavenProject project) {
    }

    public void projectRemoved(MavenProject project) {
    }
  }
}
