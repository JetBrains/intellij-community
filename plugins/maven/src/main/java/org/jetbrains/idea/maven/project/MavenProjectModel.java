package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;
import org.jetbrains.idea.maven.core.util.Tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MavenProjectModel {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenProjectModel");

  @NotNull
  private final List<Node> rootProjects = new ArrayList<Node>();

  public MavenProjectModel(Collection<VirtualFile> filesToImport,
                           Map<VirtualFile, Module> existingModules,
                           List<String> activeProfiles,
                           MavenProjectReader projectReader,
                           Map<ProjectId, VirtualFile> mapping,
                           MavenProgress p) throws MavenException, CanceledException {
    for (VirtualFile f : filesToImport) {
      p.checkCanceled();
      MavenProjectModel.Node node = createMavenTree(projectReader, f, existingModules, activeProfiles, true, mapping, p);
      rootProjects.add(node);
    }
  }

  @NotNull
  public List<Node> getRootProjects() {
    return rootProjects;
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
        LOG.info("Cannot find maven module " + modulePath);
        continue;
      }

      Node existingRoot = findExistingRoot(childFile);
      if (existingRoot != null) {
        rootProjects.remove(existingRoot);
        parentNode.mySubProjects.add(existingRoot);
      }
      else {
        Node module = createMavenTree(reader, childFile, existingModules, profiles, isExistingModuleTree, mapping, p);
        parentNode.mySubProjects.add(module);
      }
    }
  }

  private Node findExistingRoot(final VirtualFile childFile) {
    return visit(new MavenProjectVisitor<Node>() {
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

  public void resolve(final MavenProjectReader projectReader, final List<String> profiles, final MavenProgress p)
      throws MavenException, CanceledException {
    try {
      visit(new MavenProjectVisitorPlain() {
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

  abstract static class MavenProjectVisitor<Result> extends Tree.VisitorAdapter<Node, Result> {
    public boolean shouldVisit(Node node) {
      return node.isIncluded();
    }

    public Iterable<Node> getChildren(Node node) {
      return node.mySubProjects;
    }
  }

  public abstract static class MavenProjectVisitorPlain extends MavenProjectVisitor<Object> {
  }

  public abstract static class MavenProjectVisitorRoot extends MavenProjectVisitorPlain {
    public Iterable<Node> getChildren(final Node node) {
      return null;
    }
  }

  public <Result> Result visit(MavenProjectVisitor<Result> visitor) {
    return Tree.visit(rootProjects, visitor);
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

    public void linkModule(Module m) {
      myLinkedModule = m;
    }

    public void resolve(MavenProjectReader projectReader, List<String> profiles) throws MavenException, CanceledException {
      myMavenProject = projectReader.resolve(getPath(), profiles);
    }
  }
}
