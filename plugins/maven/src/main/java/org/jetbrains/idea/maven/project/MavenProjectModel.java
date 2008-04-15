package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.util.*;

import java.io.File;
import java.util.*;

public class MavenProjectModel {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenProjectModel");

  @NotNull
  private final List<Node> rootProjects = new ArrayList<Node>();

  public MavenProjectModel(Collection<VirtualFile> filesToImport,
                           Map<VirtualFile, Module> existingModules,
                           Collection<String> activeProfiles,
                           MavenProjectReader projectReader,
                           Progress p) throws MavenException, CanceledException {
    for (VirtualFile f : filesToImport) {
      p.checkCanceled();
      MavenProjectModel.Node node = createMavenTree(projectReader, f, existingModules, activeProfiles, true, p);
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
                               Collection<String> profiles,
                               boolean isExistingModuleTree,
                               Progress p) throws MavenException, CanceledException {
    p.checkCanceled();
    p.setText(ProjectBundle.message("maven.reading", FileUtil.toSystemDependentName(pomFile.getPath())));

    Model mavenModel = reader.readModel(pomFile.getPath());

    Module existingModule = existingModules.get(pomFile);
    if (existingModule == null) isExistingModuleTree = false;
    if (!isExistingModuleTree) existingModule = null;

    Node node = new Node(pomFile, mavenModel, existingModule);

    createChildNodes(reader, pomFile, existingModules, profiles, mavenModel, node, isExistingModuleTree, p);
    return node;
  }

  private void createChildNodes(MavenProjectReader reader,
                                VirtualFile pomFile,
                                Map<VirtualFile, Module> existingModules,
                                Collection<String> profiles,
                                Model mavenModel,
                                Node parentNode,
                                boolean isExistingModuleTree,
                                Progress p) throws MavenException, CanceledException {
    for (String modulePath : ProjectUtil.collectAbsoluteModulePaths(mavenModel, new File(pomFile.getPath()), profiles, new HashSet<String>())) {
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
        Node module = createMavenTree(reader, childFile, existingModules, profiles, isExistingModuleTree, p);
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

  public void resolve(final MavenProjectReader projectReader, final List<String> profiles, final Progress p)
      throws MavenException, CanceledException {
    try {
      visit(new MavenProjectVisitorPlain() {
        public void visit(Node node) {
          try {
            p.checkCanceled();
            p.setText(ProjectBundle.message("maven.resolving", FileUtil.toSystemDependentName(node.getPath())));
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
    private Model myMavenModel;
    private Module myLinkedModule;

    private boolean myIncluded = true;

    private MavenProject myMavenProject;
    final List<Node> mySubProjects = new ArrayList<Node>();
    final List<Node> mySubProjectsTopoSorted = new ArrayList<Node>(); // recursive

    private Node(@NotNull VirtualFile pomFile, @NotNull Model mavenModel, Module module) {
      myPomFile = pomFile;
      myMavenModel = mavenModel;
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
    public MavenProject getMavenProject() {
      return myMavenProject;
    }

    public MavenId getMavenId() {
      return new MavenId(myMavenModel.getGroupId(),
                         myMavenModel.getArtifactId(),
                         myMavenModel.getVersion());
    }

    public ProjectId getProjectId() {
      return new ProjectId(myMavenModel);
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
      List<MavenProject> resolvedModules = new ArrayList<MavenProject>();
      myMavenProject = projectReader.readResolved(getPath(), profiles, resolvedModules);

      Map<String, Node> pathToNode = createPathToNodeMap(mySubProjects, new HashMap<String, Node>());

      mySubProjectsTopoSorted.clear();
      for (MavenProject resolvedModule : resolvedModules) {
        Node node = pathToNode.get(getNormalizedPath(resolvedModule));
        if (node != null) {
          node.myMavenProject = resolvedModule;
          mySubProjectsTopoSorted.add(node);
        }
      }
    }
  }

  private static String getNormalizedPath(MavenProject mavenProject) {
    return new Path(mavenProject.getFile().getPath()).getPath();
  }

  private static Map<String, Node> createPathToNodeMap(List<Node> mavenModules, Map<String, Node> pathToNode) {
    for (Node mavenModule : mavenModules) {
      pathToNode.put(mavenModule.getPath(), mavenModule);
      createPathToNodeMap(mavenModule.mySubProjects, pathToNode);
    }
    return pathToNode;
  }
}
