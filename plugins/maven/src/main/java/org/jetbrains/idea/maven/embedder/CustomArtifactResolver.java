package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.jetbrains.idea.maven.project.MavenId;

import java.io.File;
import java.util.List;
import java.util.Map;

public class CustomArtifactResolver extends DefaultArtifactResolver {
  private Map<MavenId, VirtualFile> myProjectIdToFileMap;
  private UnresolvedArtifactsCollector myUnresolvedCollector;

  public void customize(Map<MavenId, VirtualFile> projectIdToFileMap, boolean failOnUnresolved) {
    myProjectIdToFileMap = projectIdToFileMap;
    myUnresolvedCollector = new UnresolvedArtifactsCollector(failOnUnresolved);
  }

  public void reset() {
    myProjectIdToFileMap = null;
    myUnresolvedCollector = null;
  }

  public UnresolvedArtifactsCollector getUnresolvedCollector() {
    return myUnresolvedCollector;
  }

  @Override
  public void resolve(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;
    try {
      super.resolve(artifact, remoteRepositories, localRepository);
    }
    catch (AbstractArtifactResolutionException e) {
      myUnresolvedCollector.collectAndSetResolved(artifact);
    }
  }

  @Override
  public void resolveAlways(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;
    try {
      super.resolveAlways(artifact, remoteRepositories, localRepository);
    }
    catch (AbstractArtifactResolutionException e) {
      myUnresolvedCollector.collectAndSetResolved(artifact);
    }
  }

  private boolean resolveAsModule(Artifact a) {
    // method is called from different threads, so we have to copy the reference so ensure there is no race cconditions.
    Map<MavenId, VirtualFile> map = myProjectIdToFileMap;
    if (map == null) return false;

    VirtualFile file = map.get(new MavenId(a));
    if (file == null) return false;

    a.setResolved(true);
    a.setFile(new File(file.getPath()));

    return true;
  }
}
