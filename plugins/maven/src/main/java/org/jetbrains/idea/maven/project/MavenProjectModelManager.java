package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.model.Parent;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.Tree;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;

import java.io.File;
import java.util.*;

public class MavenProjectModelManager {
  private List<String> myProfiles = new ArrayList<String>();
  private List<MavenProjectModel> myRootProjects = new ArrayList<MavenProjectModel>();
  private HashMap<MavenId, MavenProjectModel> myMavenIdToProject = new HashMap<MavenId, MavenProjectModel>();
  private List<Listener> myListeners = new ArrayList<Listener>();

  public void read(Collection<VirtualFile> filesToImport,
                   Map<VirtualFile, Module> fileToModuleMapping,
                   List<String> activeProfiles,
                   MavenCoreSettings mavenSettings,
                   MavenImporterSettings importerSettings,
                   MavenProcess p) throws CanceledException {
    myProfiles = activeProfiles;

    update(filesToImport, mavenSettings, p, true);

    updateModulesMapping(fileToModuleMapping);
    resolveIntermoduleDependencies();
    MavenModuleNameMapper.map(this, importerSettings.getDedicatedModuleDir());
  }

  private void updateModulesMapping(final Map<VirtualFile, Module> fileToModuleMapping) {
    visit(new SimpleVisitor() {
      public void visit(MavenProjectModel each) {
        Module module = fileToModuleMapping.get(each.getFile());
        if (module != null) each.setIdeaModule(module);
      }
    });
  }

  private void resolveIntermoduleDependencies() {
    visit(new SimpleVisitor() {
      public void visit(MavenProjectModel eachNode) {
        for (Artifact eachDependency : eachNode.getDependencies()) {
          MavenProjectModel project = myMavenIdToProject.get(new MavenId(eachDependency));
          if (project != null) {
            eachDependency.setFile(new File(project.getPath()));
            eachDependency.setResolved(true);
          }
        }
      }
    });
  }

  public void update(VirtualFile f, MavenCoreSettings mavenSettings, MavenProcess p) throws CanceledException {
    update(Collections.singleton(f), mavenSettings, p, false);
  }

  private void update(Collection<VirtualFile> files, MavenCoreSettings mavenSettings, MavenProcess p, boolean force)
      throws CanceledException {
    MavenEmbedder e = MavenEmbedderFactory.createEmbedderForRead(mavenSettings, this);

    try {
      Set<VirtualFile> readFiles = new HashSet<VirtualFile>();

      for (VirtualFile each : files) {
        MavenProjectModel project = findProject(each);
        if (project == null) {
          doAdd(each, e, readFiles, p, force);
        }
        else {
          doUpdate(findAggregator(project), project, e, false, readFiles, p, force);
        }
      }
    }
    finally {
      MavenEmbedderFactory.releaseEmbedder(e);
    }
  }

  private void doAdd(final VirtualFile f, MavenEmbedder reader, Set<VirtualFile> readFiles, MavenProcess p, boolean force)
      throws CanceledException {
    MavenProjectModel newProject = new MavenProjectModel(f, null);

    MavenProjectModel intendedAggregator = visit(new Visitor<MavenProjectModel>() {
      public void visit(MavenProjectModel node) {
        if (node.getExistingModuleFiles().contains(f)) {
          setResult(node);
        }
      }
    });

    doUpdate(intendedAggregator, newProject, reader, true, readFiles, p, force);
  }

  private void doUpdate(MavenProjectModel aggregator,
                           MavenProjectModel project,
                           MavenEmbedder embedder,
                           boolean isNew,
                           Set<VirtualFile> readFiles,
                           MavenProcess p,
                           boolean force)
      throws CanceledException {

    p.checkCanceled();

    p.setText(ProjectBundle.message("maven.reading", project.getPath()));
    p.setText2("");

    List<MavenProjectModel> prevModules = project.getModules();
    Set<MavenProjectModel> prevInheritors = isNew
                                            ? new HashSet<MavenProjectModel>()
                                            : findInheritedProjects(project);

    if (!readFiles.contains(project.getFile())) {
      if (!isNew) myMavenIdToProject.remove(project.getMavenId());
      project.read(embedder, myProfiles);
      myMavenIdToProject.put(project.getMavenId(), project);
      readFiles.add(project.getFile());
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
      project.removeModule(each);
      List<MavenProjectModel> removedProjects = new ArrayList<MavenProjectModel>();
      doRemove(project, each, removedProjects);
      prevInheritors.removeAll(removedProjects);
    }

    for (VirtualFile each : existingModuleFiles) {
      MavenProjectModel child = findProject(each);
      boolean isNewChildProject = child == null;
      if (isNewChildProject) {
        child = new MavenProjectModel(each, null);
      }
      else {
        MavenProjectModel currentAggregator = findAggregator(child);
        if (currentAggregator != null && currentAggregator != project) {
          MavenLog.LOG.info("Module " + each + " is already included into " + project.getFile());
          continue;
        }
      }

      if (isNewChildProject || force) {
        doUpdate(project, child, embedder, true, readFiles, p, force);
      } else {
        reconnect(project, child);
      }
    }

    prevInheritors.addAll(findInheritedProjects(project));
    for (MavenProjectModel each : prevInheritors) {
      doUpdate(findAggregator(each), each, embedder, false, readFiles, p, force);
    }
  }

  private void reconnect(MavenProjectModel aggregator, MavenProjectModel project) {
    MavenProjectModel prevAggregator = findAggregator(project);
    if (prevAggregator != null) {
      prevAggregator.removeModule(project);
    }
    else {
      myRootProjects.remove(project);
    }

    if (aggregator != null) {
      aggregator.addModule(project);
    }
    else {
      myRootProjects.add(project);
    }
  }

  private Set<MavenProjectModel> findInheritedProjects(final MavenProjectModel project) {
    final Set<MavenProjectModel> result = new HashSet<MavenProjectModel>();
    final MavenId id = project.getMavenId();

    visit(new Visitor<Object>() {
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

  public void delete(VirtualFile f, MavenCoreSettings mavenSettings, MavenProcess p) throws CanceledException {
    final MavenProjectModel project = findProject(f);
    if (project == null) return;

    doRemove(findAggregator(project), project, new ArrayList<MavenProjectModel>());

    MavenEmbedder e = MavenEmbedderFactory.createEmbedderForRead(mavenSettings, this);
    try {
      Set<VirtualFile> updatedFiles = new HashSet<VirtualFile>();
      for (MavenProjectModel each : findInheritedProjects(project)) {
        doUpdate(findAggregator(each), each, e, false, updatedFiles, p, false);
      }
    }
    finally {
      MavenEmbedderFactory.releaseEmbedder(e);
    }
  }

  private MavenProjectModel findAggregator(final MavenProjectModel project) {
    return visit(new Visitor<MavenProjectModel>() {
      public void visit(MavenProjectModel node) {
        if (node.getModules().contains(project)) {
          setResult(node);
        }
      }
    });
  }

  private void doRemove(MavenProjectModel aggregator, MavenProjectModel project, List<MavenProjectModel> removedProjects) {
    for (MavenProjectModel each : new ArrayList<MavenProjectModel>(project.getModules())) {
      doRemove(project, each, removedProjects);
    }

    if (aggregator != null) {
      aggregator.removeModule(project);
    }
    else {
      myRootProjects.remove(project);
    }

    removedProjects.add(project);

    myMavenIdToProject.remove(project.getMavenId());
    fireRemoved(project);
  }

  public List<MavenProjectModel> getRootProjects() {
    return myRootProjects;
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
    return findProject(f, false);
  }

  private MavenProjectModel findProject(final VirtualFile f, final boolean rootsOnly) {
    return visit(new Visitor<MavenProjectModel>() {
      public void visit(final MavenProjectModel node) {
        if (node.getFile() == f) {
          setResult(node);
        }
      }

      public Iterable<MavenProjectModel> getChildren(final MavenProjectModel node) {
        return rootsOnly ? null : super.getChildren(node);
      }
    });
  }

  public MavenProjectModel findProject(Artifact artifact) {
    return myMavenIdToProject.get(new MavenId(artifact));
  }

  public void resolve(Project project,
                      MavenProcess p,
                      MavenCoreSettings coreSettings,
                      MavenArtifactSettings artifactSettings) throws CanceledException {
    MavenEmbedder embedder = MavenEmbedderFactory.createEmbedderForResolve(coreSettings, this);

    try {
      List<MavenProjectModel> projects = getProjects();
      List<Artifact> allArtifacts = new ArrayList<Artifact>();

      for (MavenProjectModel each : projects) {
        if (each.isAggregator()) continue;

        p.checkCanceled();
        p.setText(ProjectBundle.message("maven.resolving.pom", FileUtil.toSystemDependentName(each.getPath())));
        p.setText2("");
        each.resolve(embedder);
      }

      for (MavenProjectModel each : projects) {
        if (each.isAggregator()) continue;

        p.checkCanceled();
        p.setText(ProjectBundle.message("maven.generating.sources.pom", FileUtil.toSystemDependentName(each.getPath())));
        p.setText2("");
        each.generateSources(embedder);
      }

      // We have to refresh all the resolved artifacts manually in order to
      // update all the VirtualFilePointers. It is not enough to call
      // VirtualFileManager.refresh() since the newly created files will be only
      // picked by FS when FileWathcer finiches its work. And in the case of import
      // it doesn't finics in time.
      // I couldn't manage to write a test for this since behaviour of VirtualFileManager
      // and FileWatcher differs from real-life execution.
      refreshResolvedArtifacts(allArtifacts);

      MavenArtifactDownloader d = new MavenArtifactDownloader(artifactSettings, embedder, p);
      d.download(project, projects, false);
    }
    finally {
      MavenEmbedderFactory.releaseEmbedder(embedder);
    }
  }

  private void refreshResolvedArtifacts(List<Artifact> artifacts) {
    for (Artifact a : artifacts) {
      if (!a.isResolved()) continue;
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(a.getFile());
    }
  }

  public <Result> Result visit(Visitor<Result> visitor) {
    return Tree.visit(myRootProjects, visitor);
  }

  public void addListener(Listener l) {
    myListeners.add(l);
  }


  private void fireAdded(MavenProjectModel n) {
    for (Listener each : myListeners) {
      each.projectAdded(n);
    }
  }

  private void fireUpdated(MavenProjectModel n) {
    for (Listener each : myListeners) {
      each.projectUpdated(n);
    }
  }

  private void fireRemoved(MavenProjectModel n) {
    for (Listener each : myListeners) {
      each.projectRemoved(n);
    }
  }

  public static abstract class Visitor<Result> extends Tree.VisitorAdapter<MavenProjectModel, Result> {
    public boolean shouldVisit(MavenProjectModel node) {
      return node.isIncluded();
    }

    public Iterable<MavenProjectModel> getChildren(MavenProjectModel node) {
      return node.getModules();
    }
  }

  public static abstract class SimpleVisitor extends Visitor<Object> {
  }

  public static interface Listener {
    void projectAdded(MavenProjectModel n);

    void projectUpdated(MavenProjectModel n);

    void projectRemoved(MavenProjectModel n);
  }
}
