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
  private Map<ProjectId, VirtualFile> myMapping;
  private Map<Artifact, Artifact> myAlreadyResolved = new HashMap<Artifact, Artifact>();

  public void contextualize(Context context) throws ContextException {
    if (context.contains("MavenProjectsMapping")) {
      myMapping = (Map<ProjectId, VirtualFile>)context.get("MavenProjectsMapping");
    }
  }

  @Override
  public void resolve(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
      throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveFromCache(artifact)) return;
    if (resolveAsModule(artifact)) return;

    super.resolve(artifact, remoteRepositories, localRepository);
  }

  @Override
  public void resolveAlways(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
      throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveFromCache(artifact)) return;
    if (resolveAsModule(artifact)) return;

    super.resolveAlways(artifact, remoteRepositories, localRepository);
  }

  private boolean resolveFromCache(Artifact artifact) {
    Artifact resolved = myAlreadyResolved.get(artifact);
    if (resolved == null) {
      myAlreadyResolved.put(artifact, artifact);
      return false;
    }

    artifact.setResolved(resolved.isResolved());
    artifact.setFile(resolved.getFile());
    artifact.setResolvedVersion(resolved.getVersion());
    artifact.setRepository(resolved.getRepository());

    return true;
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