package org.jetbrains.idea.maven.embedder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.io.File;
import java.util.List;

public class CustomArtifactResolver extends DefaultArtifactResolver {
  private MavenProjectsTree myProjectsTree;

  public void customize(MavenProjectsTree tree) {
    myProjectsTree = tree;
  }

  public void reset() {
    myProjectsTree = null;
  }

  @Override
  public void resolve(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;
    super.resolve(artifact, remoteRepositories, localRepository);
  }

  @Override
  public void resolveAlways(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;
    super.resolveAlways(artifact, remoteRepositories, localRepository);
  }

  private boolean resolveAsModule(Artifact a) {
    if (myProjectsTree == null) return false;

    MavenProject project = myProjectsTree.findProject(a);
    if (project == null) return false;

    a.setResolved(true);
    a.setFile(new File(project.getPath()));

    return true;
  }
}
