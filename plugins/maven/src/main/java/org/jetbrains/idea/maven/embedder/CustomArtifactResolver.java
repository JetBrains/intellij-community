package org.jetbrains.idea.maven.embedder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.apache.maven.project.build.model.DefaultModelLineageBuilder;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.io.File;
import java.util.List;

public class CustomArtifactResolver extends DefaultArtifactResolver {
  private boolean myCustomized = false;

  private MavenProjectsTree myProjectsTree;
  private CustomWagonManagerHelper myWagonManagerHelper = new CustomWagonManagerHelper(DefaultArtifactResolver.class, this);

  public void customize(MavenProjectsTree tree) {
    myCustomized = true;
    myProjectsTree = tree;
  }

  public void reset() {
    myCustomized = false;
    myProjectsTree = null;
  }

  @Override
  public void resolve(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (!myCustomized) {
      super.resolve(artifact, remoteRepositories, localRepository);
      return;
    }

    if (resolveAsModule(artifact)) return;

    boolean shouldOpen = isResolvingParent(artifact);
    if (shouldOpen) open();
    try {
      super.resolve(artifact, remoteRepositories, localRepository);
    }
    finally {
      if (shouldOpen) close();
    }
  }

  @Override
  public void resolveAlways(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (!myCustomized) {
      super.resolveAlways(artifact, remoteRepositories, localRepository);
    }

    if (resolveAsModule(artifact)) return;

    boolean shouldOpen = isResolvingParent(artifact);
    if (shouldOpen) open();
    try {
      super.resolveAlways(artifact, remoteRepositories, localRepository);
    }
    finally {
      if (shouldOpen) close();
    }
  }

  private boolean isResolvingParent(Artifact artifact) {
    if (!"pom".equals(artifact.getType()) && artifact.getScope() == null) return false;

    StackTraceElement[] stack = new Throwable().getStackTrace();
    for (StackTraceElement each : stack) {
      if (each.getClassName().equals(DefaultModelLineageBuilder.class.getName())
          && each.getMethodName().equals("resolveParentPom")) {
        return true;
      }
    }
    return false;
  }

  private boolean resolveAsModule(Artifact a) {
    MavenProject project = myProjectsTree.findProject(a);
    if (project == null) return false;

    a.setResolved(true);
    a.setFile(new File(project.getPath()));

    return true;
  }

  public void open() {
    myWagonManagerHelper.open();
  }

  public void close() {
    myWagonManagerHelper.close();
  }
}
