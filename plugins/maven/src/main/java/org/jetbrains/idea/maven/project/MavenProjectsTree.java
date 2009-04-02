package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.RuntimeInterruptedException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.*;
import java.util.*;

public class MavenProjectsTree {
  private static final String VERSION = MavenProjectsTree.class.getSimpleName() + ".1";

  private ReentrantWriterPreferenceReadWriteLock myLock = new ReentrantWriterPreferenceReadWriteLock();

  private List<String> myManagedFilesPaths = new ArrayList<String>();
  private List<String> myActiveProfiles = new ArrayList<String>();

  private List<MavenProject> myRootProjects = new ArrayList<MavenProject>();
  private HashMap<MavenId, MavenProject> myMavenIdToProject = new HashMap<MavenId, MavenProject>();
  private Map<MavenProject, List<MavenProject>> myModuleMapping = new HashMap<MavenProject, List<MavenProject>>();

  private long myLastGlobalSettingsTimestamp;
  private long myLastUserSettingsTimestamp;

  private List<Listener> myListeners = new ArrayList<Listener>();

  public static MavenProjectsTree read(File file) throws IOException {
    FileInputStream fs = new FileInputStream(file);
    DataInputStream in = new DataInputStream(fs);

    MavenProjectsTree result = new MavenProjectsTree();
    try {
      if (!VERSION.equals(in.readUTF())) return null;
      result.myManagedFilesPaths = readList(in);
      result.myActiveProfiles = readList(in);
      result.myLastGlobalSettingsTimestamp = in.readLong();
      result.myLastUserSettingsTimestamp = in.readLong();
      result.myRootProjects = readProjectsRecursively(in, result.myMavenIdToProject, result.myModuleMapping);
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
                                                            Map<MavenProject, List<MavenProject>> moduleMapping) throws IOException {
    int count = in.readInt();
    List<MavenProject> result = new ArrayList<MavenProject>(count);
    while (count-- > 0) {
      MavenProject project = MavenProject.read(in);
      List<MavenProject> modules = readProjectsRecursively(in, mavenIdToProject, moduleMapping);
      // todo what otherwise?
      if (project != null) {
        result.add(project);
        mavenIdToProject.put(project.getMavenId(), project);
        moduleMapping.put(project, modules);
      }
    }
    return result;
  }

  public void save(File file) throws IOException {
    file.getParentFile().mkdirs();
    FileOutputStream fs = new FileOutputStream(file);
    DataOutputStream out = new DataOutputStream(fs);
    try {
      out.writeUTF(VERSION);
      writeList(out, myManagedFilesPaths);
      writeList(out, myActiveProfiles);
      out.writeLong(myLastGlobalSettingsTimestamp);
      out.writeLong(myLastUserSettingsTimestamp);
      writeProjectsRecursively(out, myRootProjects);
    }
    finally {
      out.close();
      fs.close();
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
      writeProjectsRecursively(out, getModules(each));
    }
  }

  public List<String> getManagedFilesPaths() {
    return myManagedFilesPaths;
  }

  public void setManagedFilesPaths(List<String> paths) {
    myManagedFilesPaths = paths;
  }

  public void setManagedFiles(List<VirtualFile> files) {
    myManagedFilesPaths.clear();
    for (VirtualFile each : files) {
      myManagedFilesPaths.add(each.getPath());
    }
  }

  public void addManagedFile(VirtualFile file) {
    myManagedFilesPaths.add(file.getPath());
  }

  public List<VirtualFile> removeManagedFiles(List<VirtualFile> files) {
    List<VirtualFile> removedFiles = new ArrayList<VirtualFile>();
    for (VirtualFile each : files) {
      if (myManagedFilesPaths.remove(each.getPath())) {
        removedFiles.add(each);
      }
    }
    return removedFiles;
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
    myActiveProfiles = activeProfiles;
  }

  public void updateAll(Project project,
                        boolean quickUpdate,
                        MavenEmbeddersManager embeddersManager,
                        MavenGeneralSettings settings,
                        MavenConsole console,
                        MavenProcess process) throws MavenProcessCanceledException {
    boolean settingsHaveChanged = haveSettingsChanged(settings, true);
    update(project,
           getExistingManagedFiles(),
           quickUpdate,
           embeddersManager,
           console,
           process,
           false,
           settingsHaveChanged,
           true);
  }

  private boolean haveSettingsChanged(MavenGeneralSettings mavenSettings, boolean reset) {
    File globalSettings = MavenEmbedderFactory.resolveGlobalSettingsFile(mavenSettings.getMavenHome());
    File userSettings = MavenEmbedderFactory.resolveUserSettingsFile(mavenSettings.getMavenSettingsFile());
    long globalSettingsTimestamp = globalSettings == null ? -1 : globalSettings.lastModified();
    long userSettingsTimestamp = userSettings == null ? -1 : userSettings.lastModified();

    boolean settingsHaveChanged = myLastGlobalSettingsTimestamp != globalSettingsTimestamp
                                  || myLastUserSettingsTimestamp != userSettingsTimestamp;
    if (reset) {
      myLastGlobalSettingsTimestamp = globalSettingsTimestamp;
      myLastUserSettingsTimestamp = userSettingsTimestamp;
    }
    return settingsHaveChanged;
  }

  public void update(Project project,
                     Collection<VirtualFile> files,
                     boolean quickUpdate,
                     MavenEmbeddersManager embeddersManager,
                     MavenConsole console,
                     MavenProcess process) throws MavenProcessCanceledException {
    update(project, files, quickUpdate, embeddersManager, console, process, false, false, false);
  }

  private void update(Project project,
                      Collection<VirtualFile> files,
                      boolean quickUpdate,
                      MavenEmbeddersManager embeddersManager,
                      MavenConsole console,
                      MavenProcess process,
                      boolean parentHasChanged,
                      boolean settingsHaveChanged,
                      boolean recursive) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForRead(this, console, process);

    try {
      Set<VirtualFile> readFiles = new HashSet<VirtualFile>();
      Stack<MavenProject> updateStack = new Stack<MavenProject>();

      for (VirtualFile each : files) {
        MavenProject mavenProject = findProject(each);
        if (mavenProject == null) {
          doAdd(project, each, quickUpdate, embedder, readFiles, updateStack, process, parentHasChanged, settingsHaveChanged, recursive);
        }
        else {
          doUpdate(project,
                   findAggregator(mavenProject),
                   mavenProject,
                   quickUpdate,
                   embedder,
                   false,
                   readFiles,
                   updateStack,
                   process,
                   parentHasChanged,
                   settingsHaveChanged,
                   recursive);
        }
      }
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  private void doAdd(Project project,
                     final VirtualFile f,
                     boolean quickUpdate,
                     MavenEmbedderWrapper embedder,
                     Set<VirtualFile> readFiles,
                     Stack<MavenProject> updateStack,
                     MavenProcess process,
                     boolean parentHasChanged,
                     boolean settingsHaveChanged,
                     boolean recursuve) throws MavenProcessCanceledException {
    MavenProject newMavenProject = new MavenProject(f);

    MavenProject intendedAggregator = visit(new Visitor<MavenProject>() {
      public void visit(MavenProject node) {
        if (node.getExistingModuleFiles().contains(f)) {
          setResult(node);
        }
      }
    });

    doUpdate(project,
             intendedAggregator,
             newMavenProject,
             quickUpdate,
             embedder,
             true,
             readFiles,
             updateStack,
             process,
             parentHasChanged,
             settingsHaveChanged,
             recursuve);
  }

  private void doUpdate(Project project,
                        MavenProject aggregator,
                        MavenProject mavenProject,
                        boolean quickUpdate,
                        MavenEmbedderWrapper embedder,
                        boolean isNew,
                        Set<VirtualFile> readFiles,
                        Stack<MavenProject> updateStack,
                        MavenProcess process,
                        boolean parentHasChanged,
                        boolean settingsHaveChanged,
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
      if (readFiles.contains(f)) {
        assert false : "new file alread read " + f;
      }
    }

    boolean shouldRead = ((settingsHaveChanged || mavenProject.isOutdated(myActiveProfiles))
                         && !readFiles.contains(mavenProject.getFile())) || parentHasChanged;

    org.apache.maven.project.MavenProject nativeMavenProject = null;
    if (shouldRead) {
      writeLock();
      try {
        if (!isNew) myMavenIdToProject.remove(mavenProject.getMavenId());
      }
      finally {
        writeUnlock();
      }
      //if (quickUpdate) {
      //  mavenProject.readQuickly(project, myActiveProfiles);
      //} else {
        nativeMavenProject = mavenProject.read(embedder, myActiveProfiles, process);
      //}
      readFiles.add(mavenProject.getFile());

      writeLock();
      try {
        myMavenIdToProject.put(mavenProject.getMavenId(), mavenProject);
      }
      finally {
        writeUnlock();
      }

      parentHasChanged = true;

      resolveIntermoduleDependencies();
    }

    boolean reconnected = reconnect(aggregator, mavenProject);
    if (shouldRead) {
      //if (quickUpdate) {
      //  fireReadQuickly(mavenProject);
      //} else {
        fireRead(mavenProject, nativeMavenProject);
      //}
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

      if (settingsHaveChanged || recursive || isNewModule) {
        doUpdate(project,
                 mavenProject,
                 child,
                 quickUpdate,
                 embedder,
                 isNewModule,
                 readFiles,
                 updateStack,
                 process,
                 false,
                 settingsHaveChanged,
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
      doUpdate(project,
               findAggregator(each),
               each,
               quickUpdate,
               embedder,
               false,
               readFiles,
               updateStack,
               process,
               parentHasChanged,
               settingsHaveChanged,
               recursive);
    }

    updateStack.pop();
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

  public void delete(Project project,
                     List<VirtualFile> files,
                     boolean quickUpdate,
                     MavenEmbeddersManager embeddersManager,
                     MavenConsole console,
                     MavenProcess process)
    throws MavenProcessCanceledException {
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

    update(project, filesToUpdate, quickUpdate, embeddersManager, console, process, true, false, false);
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

  private MavenProject findAggregator(final MavenProject project) {
    return visit(new Visitor<MavenProject>() {
      public void visit(MavenProject node) {
        if (getModules(node).contains(project)) {
          setResult(node);
        }
      }
    });
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

  public void resolve(MavenProject project,
                      MavenEmbeddersManager embeddersManager,
                      MavenConsole console,
                      MavenProcess process) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder();
    embedder.customizeForResolve(this, console, process);

    try {
      process.checkCanceled();
      process.setText(ProjectBundle.message("maven.resolving.pom", FileUtil.toSystemDependentName(project.getPath())));
      process.setText2("");
      project.resolve(embedder, process);
      fireResolved(project);
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

  protected void fireReadQuickly(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectReadQuickly(project);
    }
  }

  protected void fireRead(MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
    for (Listener each : myListeners) {
      each.projectRead(project, nativeMavenProject);
    }
  }

  private void fireAggregatorChanged(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectAggregatorChanged(project);
    }
  }

  protected void fireResolved(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectResolved(project);
    }
  }

  private void fireRemoved(MavenProject project) {
    for (Listener each : myListeners) {
      each.projectRemoved(project);
    }
  }

  public interface Listener {
    void projectReadQuickly(MavenProject project);

    void projectRead(MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject);

    void projectAggregatorChanged(MavenProject project);

    void projectResolved(MavenProject project);

    void projectRemoved(MavenProject project);
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
}
