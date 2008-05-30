package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.jetbrains.idea.maven.core.util.ProjectId;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomArtifactResolver extends DefaultArtifactResolver implements Contextualizable {
  public static final String MAVEN_PROJECTS_MAPPING_KEY = "MAVEN_PROJECTS_MAPPING";

  private Map<ProjectId, VirtualFile> myMapping;
  private Map<Artifact, Artifact> myAlreadyResolved = new HashMap<Artifact, Artifact>();

  public void contextualize(Context context) throws ContextException {
    if (context.contains(MAVEN_PROJECTS_MAPPING_KEY)) {
      myMapping = (Map<ProjectId, VirtualFile>)context.get(MAVEN_PROJECTS_MAPPING_KEY);
    }
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
    if (myMapping == null) return false;

    VirtualFile f = myMapping.get(new ProjectId(a));
    if (f == null) return false;

    a.setResolved(true);
    a.setFile(new File(f.getPath()));

    return true;
  }
}