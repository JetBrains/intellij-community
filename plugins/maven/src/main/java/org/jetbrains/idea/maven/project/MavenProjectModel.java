package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;
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

    MavenProjectHolder mavenProject = reader.readProject(pomFile.getPath(), profiles);
    mapping.put(mavenProject.getProjectId(), pomFile);
    myImportedFiles.add(pomFile);

    Module existingModule = existingModules.get(pomFile);
    if (existingModule == null) isExistingModuleTree = false;
    if (!isExistingModuleTree) existingModule = null;

    Node node = new Node(pomFile, mavenProject, existingModule);

    createChildNodes(reader, existingModules, profiles, mavenProject, node, isExistingModuleTree, mapping, p);
    return node;
  }

  private void createChildNodes(MavenProjectReader reader,
                                Map<VirtualFile, Module> existingModules,
                                List<String> profiles,
                                MavenProjectHolder mavenProject,
                                Node parentNode,
                                boolean isExistingModuleTree,
                                Map<ProjectId, VirtualFile> mapping,
                                MavenProgress p) throws MavenException, CanceledException {
    for (String modulePath : mavenProject.getModulePaths(profiles)) {
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
        Node module = createMavenTree(reader, childFile, existingModules, profiles, isExistingModuleTree, mapping, p);
        parentNode.mySubProjects.add(module);
      }
    }
  }

  private Node findExistingRoot(final VirtualFile childFile) {
    for (Node each : myRootProjects) {
      if (each.getFile() == childFile) return each;
    }
    return null;
  }

  public void resolve(final MavenProjectReader projectReader, final List<String> profiles, final MavenProgress p)
      throws MavenException, CanceledException {
    try {
      visit(new PlainNodeVisitor() {
        public void visit(Node node) {
          try {
            p.checkCanceled();
            p.setText(ProjectBundle.message("maven.resolving.pom", FileUtil.toSystemDependentName(node.getPath())));
            node.resolve(projectReader, profiles);
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

  public abstract static class PlainNodeVisitor extends NodeVisitor<Object> {
  }

  public <Result> Result visit(NodeVisitor<Result> visitor) {
    return Tree.visit(myRootProjects, visitor);
  }

  public static class Node {
    private VirtualFile myPomFile;
    private Module myLinkedModule;

    private boolean myIncluded = true;

    private MavenProjectHolder myMavenProject;
    final List<Node> mySubProjects = new ArrayList<Node>();

    private Node(@NotNull VirtualFile pomFile, @NotNull MavenProjectHolder mavenProject, Module module) {
      myPomFile = pomFile;
      myMavenProject = mavenProject;
      myLinkedModule = module;
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

    @NotNull
    public MavenProjectHolder getMavenProject() {
      return myMavenProject;
    }

    public MavenId getMavenId() {
      return myMavenProject.getMavenId();
    }

    public ProjectId getProjectId() {
      return myMavenProject.getProjectId();
    }

    public boolean isIncluded() {
      return myIncluded;
    }

    public void setIncluded(boolean included) {
      myIncluded = included;
    }

    public Module getLinkedModule() {
      return myLinkedModule;
    }

    public void unlinkModule() {
      myLinkedModule = null;
    }

    public void resolve(MavenProjectReader projectReader, List<String> profiles) throws MavenException, CanceledException {
      myMavenProject = projectReader.resolve(getPath(), profiles);
    }
  }
}
