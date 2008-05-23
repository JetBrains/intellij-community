package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Tree;
import org.jetbrains.idea.maven.embedder.CustomExtensionManager;
import org.jetbrains.idea.maven.embedder.EmbedderFactory;

import java.io.File;
import java.util.*;

public class MavenProjectModel {
  private List<Node> myRootProjects = new ArrayList<Node>();
  private HashMap<ProjectId, VirtualFile> myProjectIdToFileMapping = new HashMap<ProjectId, VirtualFile>();
  private List<String> myProfiles = new ArrayList<String>();

  private List<Listener> myListeners = new ArrayList<Listener>();

  public void read(Collection<VirtualFile> filesToImport,
                   Map<VirtualFile, Module> existingModules,
                   List<String> activeProfiles,
                   MavenCoreSettings mavenSettings,
                   MavenImporterSettings importerSettings,
                   MavenProcess p) throws CanceledException {
    myProfiles = activeProfiles;

    update(filesToImport, mavenSettings);

    updateModules(existingModules);
    resolveIntermoduleDependencies();
    ModuleNameMapper.map(this, importerSettings.getDedicatedModuleDir());
  }

  private void updateModules(final Map<VirtualFile, Module> existingModules) {
    visit(new PlainNodeVisitor() {
      public void visit(Node node) {
        Module module = existingModules.get(node.getFile());
        if (module != null) node.setModule(module);
      }
    });
  }

  private void resolveIntermoduleDependencies() {
    visit(new PlainNodeVisitor() {
      public void visit(Node node) {
        if (!node.isValid()) return;
        for (Artifact each : node.getDependencies()) {
          VirtualFile pomFile = myProjectIdToFileMapping.get(new ProjectId(each));
          if (pomFile != null) {
            each.setFile(new File(pomFile.getPath()));
            each.setResolved(true);
          }
        }
      }
    });
  }

  public void update(VirtualFile f, MavenCoreSettings mavenSettings) throws CanceledException {
    update(Collections.singleton(f), mavenSettings);
  }

  private void update(Collection<VirtualFile> files, MavenCoreSettings mavenSettings) throws CanceledException {
    MavenEmbedder e = EmbedderFactory.createEmbedderForRead(mavenSettings, myProjectIdToFileMapping);

    try {
      MavenProjectReader reader = new MavenProjectReader(e);
      Set<VirtualFile> updatedFiles = new HashSet<VirtualFile>();

      for (VirtualFile each : files) {
        Node n = findProject(each);
        if (n == null) {
          doAdd(each, reader, updatedFiles);
        }
        else {
          doUpdate(n, reader, false, updatedFiles);
        }
      }
    }
    finally {
      EmbedderFactory.releaseEmbedder(e);
    }
  }

  private void doAdd(final VirtualFile f, MavenProjectReader reader, Set<VirtualFile> updatedFiles) throws CanceledException {
    Node newProject = new Node(f, null);
    doUpdate(newProject, reader, true, updatedFiles);

    Node parent = visit(new NodeVisitor<Node>() {
      public void visit(Node node) {
        if (node.getExistingModuleFiles().contains(f)) {
          setResult(node);
        }
      }
    });

    if (parent != null) {
      parent.mySubProjects.add(newProject);
    }
    else {
      myRootProjects.add(newProject);
    }
  }

  private void doUpdate(Node n, MavenProjectReader reader, boolean isNew, Set<VirtualFile> updatedFiles) throws CanceledException {
    if (updatedFiles.contains(n.getFile())) return;

    List<Node> oldModules = n.mySubProjects;
    List<Node> newModules = new ArrayList<Node>();

    if (!isNew) myProjectIdToFileMapping.remove(n.getProjectId());
    n.read(reader, myProfiles);
    myProjectIdToFileMapping.put(n.getProjectId(), n.getFile());
    if (isNew) {
      fireAdded(n);
    }
    else {
      fireUpdated(n);
    }

    for (VirtualFile each : n.getExistingModuleFiles()) {
      Node child = findProject(each);
      boolean isNewChild = child == null;
      if (isNewChild) {
        child = new Node(each, null);
      }
      doUpdate(child, reader, isNewChild, updatedFiles);
      newModules.add(child);
      myRootProjects.remove(child);
    }

    oldModules.removeAll(newModules);
    for (Node each : oldModules) {
      doRemove(each);
    }

    n.mySubProjects = newModules;
  }

  public void remove(VirtualFile f) {
    final Node n = findProject(f);
    if (n == null) return;

    List<Node> list;
    if (myRootProjects.contains(n)) {
      list = myRootProjects;
    }
    else {
      list = visit(new NodeVisitor<List<Node>>() {
        public void visit(Node node) {
          if (node.mySubProjects.contains(n)) {
            setResult(node.mySubProjects);
          }
        }
      });
    }
    if (list == null) return;

    list.remove(n);
    doRemove(n);
  }

  private void doRemove(Node n) {
    for (Node each : n.getSubProjects()) {
      doRemove(each);
    }
    myProjectIdToFileMapping.remove(n.getProjectId());
    fireRemoved(n);
  }

  public List<Node> getRootProjects() {
    return myRootProjects;
  }

  public List<Node> getProjects() {
    final List<Node> result = new ArrayList<Node>();
    visit(new PlainNodeVisitor() {
      public void visit(Node node) {
        result.add(node);
      }
    });
    return result;
  }

  public Node findProject(final VirtualFile f) {
    return findProject(f, false);
  }

  private Node findProject(final VirtualFile f, final boolean rootsOnly) {
    return visit(new NodeVisitor<Node>() {
      public void visit(final Node node) {
        if (node.getFile() == f) {
          setResult(node);
        }
      }

      public Iterable<Node> getChildren(final Node node) {
        return rootsOnly ? null : super.getChildren(node);
      }
    });
  }

  public Node findProject(final ProjectId id) {
    return visit(new NodeVisitor<Node>() {
      public void visit(Node node) {
        if (node.getProjectId().equals(id)) setResult(node);
      }
    });
  }

  public void resolve(Project project,
                      List<Pair<File, List<String>>> problems,
                      MavenProcess p,
                      MavenCoreSettings coreSettings,
                      MavenArtifactSettings artifactSettings) throws CanceledException {

    MavenEmbedder embedder = EmbedderFactory.createEmbedderForResolve(coreSettings, myProjectIdToFileMapping);

    try {
      resolveNodes(new MavenProjectReader(embedder), p);
      p.checkCanceled();

      final List<MavenProjectModel.Node> projects = new ArrayList<MavenProjectModel.Node>();
      final List<Artifact> allArtifacts = new ArrayList<Artifact>();

      final LinkedHashMap<File, List<String>> problemsMap = new LinkedHashMap<File, List<String>>();

      visit(new MavenProjectModel.PlainNodeVisitor() {
        public void visit(MavenProjectModel.Node node) {
          if (!node.isValid()) return;

          projects.add(node);

          MavenProject mavenProject = node.getMavenProject();
          collectUnresolvedArtifacts(mavenProject, problemsMap);
          allArtifacts.addAll((Set<Artifact>)mavenProject.getArtifacts());
        }
      });

      //collectUnresolvedBuildExtensions(EmbedderFactory.getExtensionManager(embedder), problemsMap);
      convertToList(problemsMap, problems);

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
      EmbedderFactory.releaseEmbedder(embedder);
    }
  }

  private void resolveNodes(final MavenProjectReader projectReader, final MavenProcess p)
      throws CanceledException {
    try {
      visit(new PlainNodeVisitor() {
        public void visit(Node node) {
          try {
            p.checkCanceled();
            p.setText(ProjectBundle.message("maven.resolving.pom", FileUtil.toSystemDependentName(node.getPath())));
            node.resolve(projectReader);
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (RuntimeException e) {
      if (e.getCause() instanceof CanceledException) throw (CanceledException)e.getCause();
      throw e;
    }
  }

  private void refreshResolvedArtifacts(List<Artifact> artifacts) {
    for (Artifact a : artifacts) {
      if (!a.isResolved()) return;
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(a.getFile());
    }
  }

  private void collectUnresolvedArtifacts(MavenProject p, LinkedHashMap<File, List<String>> result) {
    List<String> problems = new ArrayList<String>();

    for (Artifact a : (Collection<Artifact>)p.getArtifacts()) {
      if (a.isResolved()) continue;
      problems.add("Unresolved dependency: " + a.toString());
    }

    if (problems.isEmpty()) return;
    getProblems(result, p.getFile()).addAll(problems);
  }

  private void collectUnresolvedBuildExtensions(CustomExtensionManager m,
                                                LinkedHashMap<File, List<String>> result) {
    for (Pair<File, String> each : m.getUnresolvedExtensions()) {
      List<String> problems = getProblems(result, each.first);
      problems.add("Unresolved build extension: " + each.second);
    }
  }

  private List<String> getProblems(LinkedHashMap<File, List<String>> result, File f) {
    List<String> problems = result.get(f);
    if (problems == null) {
      problems = new ArrayList<String>();
      result.put(f, problems);
    }
    return problems;
  }

  private void convertToList(LinkedHashMap<File, List<String>> map, List<Pair<File, List<String>>> list) {
    for (Map.Entry<File, List<String>> each : map.entrySet()) {
      ArrayList<String> uniqStrings = new ArrayList<String>(new LinkedHashSet<String>(each.getValue()));
      list.add(new Pair<File, List<String>>(each.getKey(), uniqStrings));
    }
  }

  public abstract static class NodeVisitor<Result> extends Tree.VisitorAdapter<Node, Result> {
    public boolean shouldVisit(Node node) {
      return node.isIncluded();
    }

    public Iterable<Node> getChildren(Node node) {
      return node.mySubProjects;
    }
  }

  public abstract static class PlainNodeVisitor extends NodeVisitor<Object> {
  }

  public <Result> Result visit(NodeVisitor<Result> visitor) {
    return Tree.visit(myRootProjects, visitor);
  }


  public void addListener(Listener l) {
    myListeners.add(l);
  }

  private void fireAdded(Node n) {
    for (Listener each : myListeners) {
      each.projectAdded(n);
    }
  }

  private void fireUpdated(Node n) {
    for (Listener each : myListeners) {
      each.projectUpdated(n);
    }
  }

  private void fireRemoved(Node n) {
    for (Listener each : myListeners) {
      each.projectRemoved(n);
    }
  }

  public static class Node {
    private VirtualFile myPomFile;
    private Module myModule;

    private boolean myIncluded = true;

    private MavenProjectHolder myMavenProjectHolder;
    private List<Node> mySubProjects = new ArrayList<Node>();

    private List<String> myProfiles;

    private String myModuleName;
    private String myModulePath;

    private Node(@NotNull VirtualFile pomFile, Module module) {
      myPomFile = pomFile;
      myModule = module;
    }

    public List<String> getActiveProfiles() {
      return myProfiles;
    }

    public boolean isValid() {
      return myMavenProjectHolder.isValid();
    }

    public VirtualFile getFile() {
      return myPomFile;
    }

    @NotNull
    public String getPath() {
      return myPomFile.getPath();
    }

    public String getDirectory() {
      return myPomFile.getParent().getPath();
    }

    public VirtualFile getDirectoryFile() {
      return myPomFile.getParent();
    }

    public String getModuleName() {
      return myModuleName;
    }

    public void setModuleName(String moduleName) {
      myModuleName = moduleName;
    }

    public void setModuleFilePath(String modulePath) {
      myModulePath = modulePath;
    }

    public String getModuleFilePath() {
      return myModulePath;
    }

    @NotNull
    public MavenProject getMavenProject() {
      return myMavenProjectHolder.getMavenProject();
    }

    public String getProjectName() {
      return getMavenProject().getModel().getName();
    }

    public MavenId getMavenId() {
      return myMavenProjectHolder.getMavenId();
    }

    public ProjectId getProjectId() {
      return myMavenProjectHolder.getProjectId();
    }

    public boolean isIncluded() {
      return myIncluded;
    }

    public void setIncluded(boolean included) {
      myIncluded = included;
    }

    public Module getModule() {
      return myModule;
    }

    public void setModule(Module m) {
      myModule = m;
    }

    public void read(MavenProjectReader r, List<String> profiles) throws CanceledException {
      myProfiles = profiles;
      myMavenProjectHolder = r.readProject(myPomFile.getPath(), myProfiles);
    }

    public void resolve(MavenProjectReader projectReader) throws CanceledException {
      myMavenProjectHolder = projectReader.resolve(getPath(), myProfiles);
    }


    public List<String> getProblems() {
      if (!isValid()) return Collections.singletonList("pom.xml is invalid");

      List<String> result = new ArrayList<String>();

      validate(getDependencies(), "dependency", result);
      validate(getExtensions(), "build extension", result);

      return result;
    }

    private void validate(List<Artifact> artifacts, String type, List<String> result) {
      for (Artifact each : artifacts) {
        if (!each.isResolved() || !each.getFile().exists()) {
          result.add("Unresolved " + type + ": " + each);
        }
      }
    }

    public List<Node> getSubProjects() {
      return mySubProjects;
    }

    public List<VirtualFile> getExistingModuleFiles() {
      LocalFileSystem fs = LocalFileSystem.getInstance();

      List<VirtualFile> result = new ArrayList<VirtualFile>();
      for (String each : getModulePaths()) {
        VirtualFile f = fs.findFileByPath(each);
        if (f != null) result.add(f);

      }
      return result;
    }

    public List<String> getModulePaths() {
      if (!isValid()) return Collections.emptyList();
      return myMavenProjectHolder.getModulePaths(myProfiles);
    }

    public List<String> getModulePaths(Collection<String> profiles) {
      if (!isValid()) return Collections.emptyList();
      return myMavenProjectHolder.getModulePaths(profiles);
    }

    public List<String> getAllProfiles() {
      if (!isValid()) return Collections.emptyList();
      
      Set<String> result = new HashSet<String>();
      for (Profile profile : (List<Profile>)getMavenProject().getModel().getProfiles()) {
        result.add(profile.getId());
      }
      return new ArrayList<String>(result);
    }

    public List<Artifact> getDependencies() {
      if (!isValid()) return Collections.emptyList();
      return ProjectUtil.extractDependencies(this);
    }

    public List<Artifact> getExportableDependencies() {
      if (!isValid()) return Collections.emptyList();
      return ProjectUtil.extractExportableDependencies(this);
    }

    public List<MavenId> getPluginsIds() {
      List<MavenId> result = new ArrayList<MavenId>();
      for (Plugin plugin : getPlugins()) {
        result.add(new MavenId(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));
      }
      return result;
    }
    public List<Plugin> getPlugins() {
      if (!isValid()) return Collections.emptyList();

      List<Plugin> result = new ArrayList<Plugin>();

      collectPlugins(getMavenProject().getBuild(), result);
      //noinspection unchecked
      List<Profile> profiles = (List<Profile>)getMavenProject().getModel().getProfiles();
      if (profiles == null) return result;

      for (Profile profile : profiles) {
        if (profiles.contains(profile.getId())) {
          collectPlugins(profile.getBuild(), result);
        }
      }
      return result;
    }

    private void collectPlugins(BuildBase build, List<Plugin> result) {
      if (build == null) return;
      List<Plugin> plugins = (List<Plugin>)build.getPlugins();
      if (plugins == null) return;
      result.addAll(plugins);
    }


    public List<Artifact> getExtensions() {
      if (!isValid()) return Collections.emptyList();
      return new ArrayList<Artifact>(getMavenProject().getExtensionArtifacts());
    }


    @Nullable
    public String findPluginConfigurationValue(String groupId,
                                                      String artifactId,
                                                      String... nodeNames) {
      Xpp3Dom node = findPluginConfigurationNode(groupId, artifactId, nodeNames);
      if (node == null) return null;
      return node.getValue();
    }

    @Nullable
    public Xpp3Dom findPluginConfigurationNode(
                                                      String groupId,
                                                      String artifactId,
                                                      String... nodeNames) {
      Plugin plugin = findPlugin(groupId, artifactId);
      if (plugin == null) return null;

      Xpp3Dom node = (Xpp3Dom)plugin.getConfiguration();
      if (node == null) return null;

      for (String name : nodeNames) {
        node = node.getChild(name);
        if (node == null) return null;
      }

      return node;
    }

    @Nullable
    public Plugin findPlugin( String groupId, String artifactId) {
      for (Plugin each : getPlugins()) {
        if (groupId.equals(each.getGroupId()) && artifactId.equals(each.getArtifactId())) return each;
      }
      return null;
    }
  }

  public static interface Listener {
    void projectAdded(Node n);

    void projectUpdated(Node n);

    void projectRemoved(Node n);
  }
}
