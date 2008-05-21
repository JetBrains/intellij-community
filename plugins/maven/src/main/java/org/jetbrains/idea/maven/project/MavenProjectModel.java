package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Tree;

import java.util.*;

public class MavenProjectModel {
  private final List<Node> myRootProjects = new ArrayList<Node>();
  private final Set<VirtualFile> myImportedFiles = new HashSet<VirtualFile>();

  public MavenProjectModel(Collection<VirtualFile> filesToImport,
                           Map<VirtualFile, Module> existingModules,
                           List<String> activeProfiles,
                           MavenProjectReader projectReader,
                           Map<ProjectId, VirtualFile> mapping,
                           MavenProgress p) throws MavenException, CanceledException {
    for (VirtualFile f : filesToImport) {
      p.checkCanceled();

      if (myImportedFiles.contains(f)) continue;
      Node node = createMavenTree(projectReader, f, existingModules, activeProfiles, true, mapping, p);
      myRootProjects.add(node);
    }
  }

  @NotNull
  public List<Node> getRootProjects() {
    return myRootProjects;
  }

  private Node createMavenTree(MavenProjectReader reader,
                               VirtualFile pomFile,
                               Map<VirtualFile, Module> existingModules,
                               List<String> profiles,
                               boolean isExistingModuleTree,
                               Map<ProjectId, VirtualFile> mapping,
                               MavenProgress p) throws MavenException, CanceledException {
    p.checkCanceled();
    p.setText(ProjectBundle.message("maven.reading", FileUtil.toSystemDependentName(pomFile.getPath())));

    Module existingModule = existingModules.get(pomFile);
    if (existingModule == null) isExistingModuleTree = false;
    if (!isExistingModuleTree) existingModule = null;

    Node node = new Node(pomFile, existingModule, profiles);
    node.read(reader);

    mapping.put(node.getProjectId(), pomFile);
    myImportedFiles.add(pomFile);

    createChildNodes(reader, node, existingModules, isExistingModuleTree, mapping, p);
    return node;
  }

  private void createChildNodes(MavenProjectReader reader,
                                Node parentNode,
                                Map<VirtualFile, Module> existingModules,
                                boolean isExistingModuleTree,
                                Map<ProjectId, VirtualFile> mapping,
                                MavenProgress p) throws MavenException, CanceledException {
    for (String modulePath : parentNode.getModulePaths()) {
      p.checkCanceled();

      VirtualFile childFile = LocalFileSystem.getInstance().findFileByPath(modulePath);
      if (childFile == null) {
        MavenLog.LOG.info("Cannot find maven module " + modulePath);
        continue;
      }

      Node existingRoot = findExistingRoot(childFile);
      if (existingRoot != null) {
        myRootProjects.remove(existingRoot);
        parentNode.mySubProjects.add(existingRoot);
      }
      else {
        Node module = createMavenTree(reader, childFile, existingModules, parentNode.getProfiles(), isExistingModuleTree, mapping, p);
        parentNode.mySubProjects.add(module);
      }
    }
  }

  private Node findExistingRoot(final VirtualFile childFile) {
    return visit(new NodeVisitor<Node>() {
      public void visit(final Node node) {
        if (node.getFile() == childFile) {
          setResult(node);
        }
      }

      public Iterable<Node> getChildren(final Node node) {
        return null;
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

  public void resolve(final MavenProjectReader projectReader, final MavenProgress p)
      throws MavenException, CanceledException {
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
      if (e.getCause() instanceof MavenException) throw (MavenException)e.getCause();
      if (e.getCause() instanceof CanceledException) throw (CanceledException)e.getCause();
      throw e;
    }
  }

  abstract static class NodeVisitor<Result> extends Tree.VisitorAdapter<Node, Result> {
    public boolean shouldVisit(Node node) {
      return node.isIncluded();
    }

    public Iterable<Node> getChildren(Node node) {
      return node.mySubProjects;
    }
  }

  abstract static class PlainNodeVisitor extends NodeVisitor<Object> {
  }

  public <Result> Result visit(NodeVisitor<Result> visitor) {
    return Tree.visit(myRootProjects, visitor);
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

    private Node(@NotNull VirtualFile pomFile, Module module, List<String> profiles) {
      myPomFile = pomFile;
      myModule = module;
      myProfiles = profiles;
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

    @SuppressWarnings({"ConstantConditions"})
    @NotNull
    public String getDirectory() {
      return myPomFile.getParent().getPath();
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

    public void read(MavenProjectReader r) throws CanceledException {
      myMavenProjectHolder = r.readProject(myPomFile.getPath(), myProfiles);
    }

    public void resolve(MavenProjectReader projectReader) throws MavenException, CanceledException {
      myMavenProjectHolder = projectReader.resolve(getPath(), myProfiles);
    }

    public List<Node> getSubProjects() {
      return mySubProjects;
    }

    public List<Artifact> extractDependencies() {
      return ProjectUtil.extractDependencies(this);
    }

    public List<Artifact> extractExportableDependencies() {
      return ProjectUtil.extractExportableDependencies(this);
    }

    public List<String> getModulePaths() {
      return myMavenProjectHolder.getModulePaths(myProfiles);
    }

    public List<String> getModulePaths(Collection<String> profiles) {
      return myMavenProjectHolder.getModulePaths(profiles);
    }

    public List<String> getProfiles() {
      return myProfiles;
    }
  }
}
