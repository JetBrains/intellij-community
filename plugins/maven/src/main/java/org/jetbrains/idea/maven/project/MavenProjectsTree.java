package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.RuntimeInterruptedException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Parent;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;

import java.io.File;
import java.util.*;

public class MavenProjectsTree {
  private ReentrantWriterPreferenceReadWriteLock lock = new ReentrantWriterPreferenceReadWriteLock();

  private List<String> myProfiles = new ArrayList<String>();
  private List<MavenProjectModel> myRootProjects = new ArrayList<MavenProjectModel>();
  private HashMap<MavenId, MavenProjectModel> myMavenIdToProject = new HashMap<MavenId, MavenProjectModel>();
  private List<Listener> myListeners = new ArrayList<Listener>();

  private Map<MavenProjectModel, List<MavenProjectModel>> myModuleMapping = new HashMap<MavenProjectModel, List<MavenProjectModel>>();

  public void read(Collection<VirtualFile> filesToImport,
                   List<String> activeProfiles,
                   MavenCoreSettings mavenSettings,
                   MavenProcess p) throws MavenProcessCanceledException {
    myProfiles = activeProfiles;
    update(filesToImport, mavenSettings, p, true);
    resolveIntermoduleDependencies();
  }

  private void resolveIntermoduleDependencies() {
    for (MavenProjectModel eachProject : getProjects()) {
      for (Artifact eachDependency : eachProject.getDependencies()) {
        MavenProjectModel project = myMavenIdToProject.get(new MavenId(eachDependency));
        if (project != null) {
          eachDependency.setFile(new File(project.getPath()));
          eachDependency.setResolved(true);
        }
      }
    }
  }

  public void update(Collection<VirtualFile> files, MavenCoreSettings mavenSettings, MavenProcess p) throws MavenProcessCanceledException {
    update(files, mavenSettings, p, false);
  }

  private void update(Collection<VirtualFile> files, MavenCoreSettings mavenSettings, MavenProcess p, boolean force)
      throws MavenProcessCanceledException {
    MavenEmbedderWrapper e = MavenEmbedderFactory.createEmbedderForRead(mavenSettings, null, this);

    try {
      Set<VirtualFile> readFiles = new HashSet<VirtualFile>();
      Stack<MavenProjectModel> updateStack = new Stack<MavenProjectModel>();

      for (VirtualFile each : files) {
        MavenProjectModel project = findProject(each);
        if (project == null) {
          doAdd(each, e, readFiles, updateStack, p, force);
        }
        else {
          doUpdate(findAggregator(project), project, e, false, readFiles, updateStack, p, force);
        }
      }
    }
    finally {
      e.release();
    }
  }

  private void doAdd(final VirtualFile f,
                     MavenEmbedderWrapper reader,
                     Set<VirtualFile> readFiles,
                     Stack<MavenProjectModel> updateStack,
                     MavenProcess p, boolean force)
      throws MavenProcessCanceledException {
    MavenProjectModel newProject = new MavenProjectModel(f);

    MavenProjectModel intendedAggregator = visit(new Visitor<MavenProjectModel>() {
      public void visit(MavenProjectModel node) {
        if (node.getExistingModuleFiles().contains(f)) {
          setResult(node);
        }
      }
    });

    doUpdate(intendedAggregator, newProject, reader, true, readFiles, updateStack, p, force);
  }

  private void doUpdate(MavenProjectModel aggregator,
                        MavenProjectModel project,
                        MavenEmbedderWrapper embedder,
                        boolean isNew,
                        Set<VirtualFile> readFiles,
                        Stack<MavenProjectModel> updateStack,
                        MavenProcess p,
                        boolean force) throws MavenProcessCanceledException {
    p.checkCanceled();

    if (updateStack.contains(project)) {
      MavenLog.LOG.info("Recursion detected in " + project.getFile());
      return;
    }
    updateStack.push(project);

    p.setText(ProjectBundle.message("maven.reading", project.getPath()));
    p.setText2("");

    List<MavenProjectModel> prevModules = getModules(project);
    Set<MavenProjectModel> prevInheritors = isNew
                                            ? new HashSet<MavenProjectModel>()
                                            : findInheritors(project);

    // todo hook for IDEADEV-27583
    if (isNew) {
      VirtualFile f = project.getFile();
      assert !readFiles.contains(f) : "new file alread read " + f;
    }

    if (!readFiles.contains(project.getFile())) {
      writeLock();
      try {
        if (!isNew) myMavenIdToProject.remove(project.getMavenId());
      }
      finally {
        writeUnlock();

      }
      project.read(embedder, myProfiles);
      readFiles.add(project.getFile());

      writeLock();
      try {
        myMavenIdToProject.put(project.getMavenId(), project);
      }
      finally {
        writeUnlock();
      }
    }

    reconnect(aggregator, project);

    if (isNew) {
      fireAdded(project);
    }
    else {
      fireUpdated(project);
    }

    List<VirtualFile> existingModuleFiles = project.getExistingModuleFiles();
    List<MavenProjectModel> modulesToRemove = new ArrayList<MavenProjectModel>();
    for (MavenProjectModel each : prevModules) {
      if (!existingModuleFiles.contains(each.getFile())) {
        modulesToRemove.add(each);
      }
    }
    for (MavenProjectModel each : modulesToRemove) {
      removeModule(project, each);
      List<MavenProjectModel> removedProjects = new ArrayList<MavenProjectModel>();
      doRemove(project, each, removedProjects);
      prevInheritors.removeAll(removedProjects);
    }

    for (VirtualFile each : existingModuleFiles) {
      MavenProjectModel child = findProject(each);
      boolean isNewChildProject = child == null;
      if (isNewChildProject) {
        child = new MavenProjectModel(each);
      }
      else {
        MavenProjectModel currentAggregator = findAggregator(child);
        if (currentAggregator != null && currentAggregator != project) {
          MavenLog.LOG.info("Module " + each + " is already included into " + project.getFile());
          continue;
        }
      }

      if (isNewChildProject || force) {
        doUpdate(project, child, embedder, isNewChildProject, readFiles, updateStack, p, force);
      }
      else {
        reconnect(project, child);
      }
    }


    Set<MavenProjectModel> allInheritors = findInheritors(project);
    allInheritors.addAll(prevInheritors);
    for (MavenProjectModel each : allInheritors) {
      doUpdate(findAggregator(each), each, embedder, false, readFiles, updateStack, p, force);
    }

    updateStack.pop();
  }

  private void reconnect(MavenProjectModel aggregator, MavenProjectModel project) {
    MavenProjectModel prevAggregator = findAggregator(project);

    writeLock();
    try {
      if (prevAggregator != null) {
        removeModule(prevAggregator, project);
      }
      else {
        myRootProjects.remove(project);
      }

      if (aggregator != null) {
        addModule(aggregator, project);
      }
      else {
        myRootProjects.add(project);
      }
    }
    finally {
      writeUnlock();
    }
  }

  public void delete(List<VirtualFile> files, MavenCoreSettings mavenSettings, MavenProcess p) throws MavenProcessCanceledException {
    List<MavenProjectModel> projectsToUpdate = new ArrayList<MavenProjectModel>();
    List<MavenProjectModel> removedProjects = new ArrayList<MavenProjectModel>();

    for (VirtualFile each : files) {
      p.checkCanceled();

      MavenProjectModel project = findProject(each);
      if (project == null) return;

      projectsToUpdate.addAll(findInheritors(project));
      doRemove(findAggregator(project), project, removedProjects);
    }

    projectsToUpdate.removeAll(removedProjects);

    List<VirtualFile> filesToUpdate = new ArrayList<VirtualFile>();
    for (MavenProjectModel each : projectsToUpdate) {
      filesToUpdate.add(each.getFile());
    }

    update(filesToUpdate, mavenSettings, p);
  }

  private void doRemove(MavenProjectModel aggregator, MavenProjectModel project, List<MavenProjectModel> removedProjects)
      throws MavenProcessCanceledException {
    for (MavenProjectModel each : getModules(project)) {
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

  private Set<MavenProjectModel> findInheritors(final MavenProjectModel project) {
    final Set<MavenProjectModel> result = new HashSet<MavenProjectModel>();
    final MavenId id = project.getMavenId();

    visit(new SimpleVisitor() {
      public void visit(MavenProjectModel each) {
        if (each == project) return;
        MavenId parentId = getParentId(each);
        if (id.equals(parentId)) result.add(each);
      }
    });
    return result;
  }

  private MavenId getParentId(MavenProjectModel project) {
    Parent parent = project.getMavenProject().getModel().getParent();
    if (parent == null) return null;
    return new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
  }

  private MavenProjectModel findAggregator(final MavenProjectModel project) {
    return visit(new Visitor<MavenProjectModel>() {
      public void visit(MavenProjectModel node) {
        if (getModules(node).contains(project)) {
          setResult(node);
        }
      }
    });
  }

  public List<MavenProjectModel> getRootProjects() {
    readLock();
    try {
      return new ArrayList<MavenProjectModel>(myRootProjects);
    }
    finally {
      readUnlock();
    }
  }

  public List<MavenProjectModel> getProjects() {
    final List<MavenProjectModel> result = new ArrayList<MavenProjectModel>();
    visit(new SimpleVisitor() {
      public void visit(MavenProjectModel node) {
        result.add(node);
      }
    });
    return result;
  }

  public MavenProjectModel findProject(final VirtualFile f) {
    return visit(new Visitor<MavenProjectModel>() {
      public void visit(final MavenProjectModel node) {
        if (node.getFile() == f) {
          setResult(node);
        }
      }
    });
  }

  public MavenProjectModel findProject(MavenId id) {
    readLock();
    try {
      return myMavenIdToProject.get(id);
    }
    finally {
      readUnlock();
    }
  }

  public MavenProjectModel findProject(Artifact artifact) {
    return findProject(new MavenId(artifact));
  }

  public boolean isModuleOf(MavenProjectModel aggregator, MavenProjectModel module) {
    return getModules(aggregator).contains(module);
  }

  public List<MavenProjectModel> getModules(MavenProjectModel aggregator) {
    readLock();
    try {
      List<MavenProjectModel> modules = myModuleMapping.get(aggregator);
      return modules == null
             ? Collections.<MavenProjectModel>emptyList()
             : new ArrayList<MavenProjectModel>(modules);
    }
    finally {
      readUnlock();
    }
  }

  private void addModule(MavenProjectModel aggregator, MavenProjectModel module) {
    writeLock();
    try {
      List<MavenProjectModel> modules = myModuleMapping.get(aggregator);
      if (modules == null) {
        modules = new ArrayList<MavenProjectModel>();
        myModuleMapping.put(aggregator, modules);
      }
      modules.add(module);
    }
    finally {
      writeUnlock();
    }
  }

  private void removeModule(MavenProjectModel aggregator, MavenProjectModel module) {
    writeLock();
    try {
      List<MavenProjectModel> modules = myModuleMapping.get(aggregator);
      if (modules == null) return;
      modules.remove(module);
    }
    finally {
      writeUnlock();
    }
  }

  public void resolve(MavenCoreSettings coreSettings,
                      MavenArtifactSettings artifactSettings,
                      MavenProcess p) throws MavenProcessCanceledException {
    MavenEmbedderWrapper e = MavenEmbedderFactory.createEmbedderForResolve(coreSettings, null, this);

    try {
      List<MavenProjectModel> projects = getProjects();

      for (MavenProjectModel each : projects) {
        p.checkCanceled();
        p.setText(ProjectBundle.message("maven.resolving.pom", FileUtil.toSystemDependentName(each.getPath())));
        p.setText2("");
        each.resolve(e);
        fireUpdated(each);
      }

      doDownload(artifactSettings, p, e, projects, false);
    }
    finally {
      e.release();
    }
  }

  public void generateSources(MavenCoreSettings coreSettings,
                              MavenProcess p) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = MavenEmbedderFactory.createEmbedderForResolve(coreSettings, null, this);

    try {
      for (MavenProjectModel each : getProjects()) {
        p.checkCanceled();
        p.setText(ProjectBundle.message("maven.generating.sources.pom", FileUtil.toSystemDependentName(each.getPath())));
        p.setText2("");
        each.generateSources(embedder);
      }
    }
    finally {
      embedder.release();
    }
  }

  public void download(MavenCoreSettings coreSettings,
                       MavenArtifactSettings artifactSettings,
                       MavenProcess p) throws MavenProcessCanceledException {
    MavenEmbedderWrapper e = MavenEmbedderFactory.createEmbedderForExecute(coreSettings, null);
    try {
      doDownload(artifactSettings, p, e, getProjects(), true);
    }
    finally {
      e.release();
    }
  }

  private void doDownload(MavenArtifactSettings artifactSettings,
                          MavenProcess p,
                          MavenEmbedderWrapper embedder,
                          List<MavenProjectModel> projects,
                          boolean demand) throws MavenProcessCanceledException {
    MavenArtifactDownloader.download(this, projects, artifactSettings, demand, embedder, p);
  }

  public Dependency addDependency(Project project,
                                  MavenProjectModel mavenProject,
                                  Artifact artifact) throws MavenProcessCanceledException {
    return mavenProject.addDependency(project, artifact);
  }

  public Artifact downloadArtifact(MavenProjectModel mavenProject,
                                   MavenId id,
                                   MavenCoreSettings coreSettings) throws MavenProcessCanceledException {
    MavenEmbedderWrapper e = MavenEmbedderFactory.createEmbedderForExecute(coreSettings, null);
    try {
      Artifact artifact = e.createArtifact(id.groupId,
                                           id.artifactId,
                                           id.version,
                                           MavenConstants.JAR_TYPE,
                                           null);
      artifact.setScope(Artifact.SCOPE_COMPILE);
      e.resolve(artifact, mavenProject.getRepositories());
      return artifact;
    }
    finally {
      e.release();
    }
  }

  public <Result> Result visit(Visitor<Result> visitor) {
    for (MavenProjectModel each : getRootProjects()) {
      if (visitor.isDone()) break;
      doVisit(each, visitor);
    }
    return visitor.getResult();
  }

  private <Result> void doVisit(MavenProjectModel project, Visitor<Result> visitor) {
    if (!visitor.isDone() && visitor.shouldVisit(project)) {
      visitor.visit(project);
      for (MavenProjectModel each : getModules(project)) {
        if (visitor.isDone()) break;
        doVisit(each, visitor);
      }
      visitor.leave(project);
    }
  }

  private void writeLock() {
    try {
      lock.writeLock().acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  private void writeUnlock() {
    lock.writeLock().release();
  }

  private void readLock() {
    try {
      lock.readLock().acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  private void readUnlock() {
    lock.readLock().release();
  }

  public void addListener(Listener l) {
    myListeners.add(l);
  }

  private void fireAdded(MavenProjectModel n) {
    for (Listener each : myListeners) {
      each.projectAdded(n);
    }
  }

  protected void fireUpdated(MavenProjectModel n) {
    for (Listener each : myListeners) {
      each.projectUpdated(n);
    }
  }

  private void fireRemoved(MavenProjectModel n) {
    for (Listener each : myListeners) {
      each.projectRemoved(n);
    }
  }

  public static interface Listener {
    void projectAdded(MavenProjectModel n);

    void projectUpdated(MavenProjectModel n);

    void projectRemoved(MavenProjectModel n);
  }

  public static abstract class Visitor<Result> {
    private Result result;

    public boolean shouldVisit(MavenProjectModel node) {
      return node.isIncluded();
    }

    public abstract void visit(MavenProjectModel node);

    public void leave(MavenProjectModel node) {
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
