package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.Tree;

import java.io.File;
import java.util.*;

public class MavenProjectModel {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenProjectModel");

  private final List<Node> rootProjects = new ArrayList<Node>();

  private final MavenEmbedder mavenEmbedder;

  public MavenProjectModel(Map<VirtualFile, Module> filesToRefresh,
                           final Collection<VirtualFile> importRoots,
                           final MavenEmbedder embedder) {
    mavenEmbedder = embedder;

    if (mavenEmbedder == null) {
      return;
    }

    Map<VirtualFile, Module> fileToModule = new HashMap<VirtualFile, Module>();

    for (Map.Entry<VirtualFile, Module> entry : filesToRefresh.entrySet()) {
      fileToModule.put(entry.getKey(), entry.getValue());
    }

    for (VirtualFile file : importRoots) {
      fileToModule.put(file, null);
    }

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(ProjectBundle.message("maven.reading.pom"));
    }

    while (fileToModule.size() != 0) {
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
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      if(indicator.isCanceled()) {
        return null;
      }
      indicator.setText2(FileUtil.toSystemDependentName(pomFile.getPath()));
    }

    final Module linkedModule = unprocessedFiles.get(pomFile);
    unprocessedFiles.remove(pomFile);

    MavenProject mavenProject;
    try {
      mavenProject = mavenEmbedder.readProjectWithDependencies(new File(pomFile.getPath()), createTransferListener());
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

  private static TransferListener createTransferListener() {
    return new TransferListener() {
      @Nullable final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

      int counter = 0;
      String path;

      public void transferInitiated(TransferEvent event) {
      }

      public void transferStarted(TransferEvent event) {
        path = FileUtil.toSystemDependentName(event.getLocalFile().getPath());
        counter = 0;
        setText();
      }

      private void setText() {
        if (indicator != null) {
          indicator.setText2(ProjectBundle.message("maven.transfer.progress", counter, path));
        }
      }

      public void transferProgress(TransferEvent event, byte[] bytes, int i) {
        counter += i;
        setText();
      }

      public void transferCompleted(TransferEvent event) {
        if (indicator != null) {
          indicator.setText2("");
        }
      }

      public void transferError(TransferEvent event) {
      }

      public void debug(String s) {
      }
    };
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
    @NotNull final private MavenProject mavenProject;
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
  }
}
