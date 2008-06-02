package org.jetbrains.idea.maven.embedder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectModelManager;

import java.io.File;
import java.util.List;

public class CustomArtifactResolver extends DefaultArtifactResolver implements Contextualizable {
  public static final String MAVEN_PROJECT_MODEL_MANAGER = "MAVEN_PROJECT_MODEL_MANAGER";
  private MavenProjectModelManager myModelManager;

  public void contextualize(Context context) throws ContextException {
    myModelManager = (MavenProjectModelManager)context.get(MAVEN_PROJECT_MODEL_MANAGER);
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
    MavenProjectModel project = myModelManager.findProject(a);
    if (project == null) return false;

    a.setResolved(true);
    a.setFile(new File(project.getPath()));

    return true;
  }
}