package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.Tree;

import java.util.*;

public class MavenProjectModel {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenProjectModel");

  private final List<Node> rootProjects = new ArrayList<Node>();

  private final MavenProjectReader myProjectReader;

  public MavenProjectModel(Map<VirtualFile, Module> filesToRefresh,
                           final Collection<VirtualFile> importRoots,
                           final MavenProjectReader projectReader) {
    this.myProjectReader = projectReader;

    Map<VirtualFile, Module> fileToModule = new HashMap<VirtualFile, Module>();

    for (Map.Entry<VirtualFile, Module> entry : filesToRefresh.entrySet()) {
      fileToModule.put(entry.getKey(), entry.getValue());
    }

    for (VirtualFile file : importRoots) {
      fileToModule.put(file, null);
    }

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    while (fileToModule.size() != 0) {
      if (indicator != null && indicator.isCanceled()) {
        break;
      }
      final MavenProjectModel.Node node = createMavenTree(fileToModule.keySet().iterator().next(), fileToModule, false);
      if (node != null) {
        rootProjects.add(node);
      }
    }
  }

  public List<Node> getRootProjects() {
    return rootProjects;
  }

  @Nullable
  private Node createMavenTree(@NotNull VirtualFile pomFile, final Map<VirtualFile, Module> unprocessedFiles, boolean imported) {
    final Module linkedModule = unprocessedFiles.get(pomFile);
    unprocessedFiles.remove(pomFile);

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null && indicator.isCanceled()) {
      return null;
    }
    if (indicator != null) {
      indicator.setText(ProjectBundle.message("maven.reading.pom", FileUtil.toSystemDependentName(pomFile.getPath())));
    }

    MavenProject mavenProject;
    try {
      mavenProject = myProjectReader.readBare(pomFile.getPath());
    }
    catch (Exception e) {
      LOG.info(e);
      return null;
    }

    if (mavenProject == null) {
      LOG.info("Cannot load " + pomFile.getPath());
      return null;
    }

    imported |= linkedModule == null;

    final Node node = new Node(pomFile, mavenProject, imported ? null : linkedModule);

    for (Object moduleName : mavenProject.getModules()) {
      if (indicator != null && indicator.isCanceled()) {
        return null;
      }
      VirtualFile childFile = getMavenModuleFile(pomFile, (String)moduleName);
      if (childFile != null) {
        final Node existingRoot = findExistingRoot(childFile);
        if (existingRoot != null) {
          rootProjects.remove(existingRoot);
          node.mavenModules.add(existingRoot);
        }
        else if (imported || unprocessedFiles.containsKey(childFile)) {
          Node module = createMavenTree(childFile, unprocessedFiles, imported);
          if (module != null) {
            node.mavenModules.add(module);
          }
        }
      }
      else {
        LOG.warn("Cannot find maven module " + moduleName);
      }
    }
    return node;
  }

  public void resolve() {
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    visit(new MavenProjectVisitorPlain() {
      public void visit(final Node node) {
        if (progressIndicator != null) {
          if (progressIndicator.isCanceled()) {
            setResult(node);
            return;
          }
          progressIndicator.setText(ProjectBundle.message("maven.resolving", FileUtil.toSystemDependentName(node.getPath())));
        }
        node.resolve();
      }
    });
  }

  @Nullable
  private static VirtualFile getMavenModuleFile(VirtualFile parent, String name) {
    //noinspection ConstantConditions
    VirtualFile moduleDir = parent.getParent().findChild(name);
    return moduleDir != null ? moduleDir.findChild(MavenEnv.POM_FILE) : null;
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

  abstract static class MavenProjectVisitor<Result> extends Tree.VisitorAdapter<Node, Result> {
    public boolean shouldVisit(Node node) {
      return node.isIncluded();
    }

    public Iterable<Node> getChildren(Node node) {
      return node.mavenModules;
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

  public class Node {

    @NotNull final private VirtualFile pomFile;
    @NotNull private MavenProject mavenProject;
    private Module linkedModule;

    private boolean included = true;

    final List<Node> mavenModules = new ArrayList<Node>();

    private Node(@NotNull VirtualFile pomFile, @NotNull final MavenProject mavenProject, final Module linkedModule) {
      this.pomFile = pomFile;
      this.mavenProject = mavenProject;
      this.linkedModule = linkedModule;
    }

    public VirtualFile getFile() {
      return pomFile;
    }

    @NotNull
    public String getPath() {
      return pomFile.getPath();
    }

    @SuppressWarnings({"ConstantConditions"})
    @NotNull
    public String getDirectory() {
      return pomFile.getParent().getPath();
    }

    @NotNull
    public MavenProject getMavenProject() {
      return mavenProject;
    }

    public Artifact getArtifact() {
      return mavenProject.getArtifact();
    }

    public boolean isIncluded() {
      return included;
    }

    public void setIncluded(final boolean included) {
      this.included = included;
    }

    public Module getLinkedModule() {
      return linkedModule;
    }

    public boolean isLinked() {
      return linkedModule != null;
    }

    public void unlinkModule() {
      linkedModule = null;
    }

    public void resolve() {
      final MavenProject resolved = myProjectReader.readResolved(getPath());
      if (resolved != null) {
        mavenProject = resolved;
      }
    }

    @NonNls
    public String createPath(@NonNls final String... elements) {
        final StringBuilder stringBuilder = new StringBuilder(getDirectory());
        for (String element : elements) {
          stringBuilder.append("/");
          stringBuilder.append(element);
        }
        return FileUtil.toSystemIndependentName(stringBuilder.toString());
      }
  }
}
