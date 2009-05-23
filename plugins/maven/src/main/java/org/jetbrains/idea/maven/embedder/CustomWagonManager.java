package org.jetbrains.idea.maven.embedder;

import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.manager.DefaultWagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.WagonException;
import org.jetbrains.idea.maven.project.MavenId;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomWagonManager extends DefaultWagonManager {
  private boolean myFailOnUnresolved = true;
  private Set<MavenId> myUnresolvedIds = new THashSet<MavenId>();

  private boolean myInProcess = false;
  private Map<String, CachedValue> myCache = new THashMap<String, CachedValue>();

  public void customize(boolean failOnUnresolved) {
    myFailOnUnresolved = failOnUnresolved;
  }

  public void reset() {
    myFailOnUnresolved = true;
  }

  public Set<MavenId> retrieveUnresolvedIds() {
    Set<MavenId> result = myUnresolvedIds;
    myUnresolvedIds = new THashSet<MavenId>();
    return result;
  }

  @Override
  public void getArtifact(Artifact artifact, List remoteRepositories, boolean force)
    throws TransferFailedException, ResourceDoesNotExistException {
    if (myInProcess) {
      super.getArtifact(artifact, remoteRepositories, force);
      return;
    }

    myInProcess = true;
    try {
      if (!takeFromCache(artifact)) {
        try {
          super.getArtifact(artifact, remoteRepositories, force);
        }
        catch (WagonException ignore) {
        }
        cache(artifact);
      }
      setResolved(artifact);
    }
    finally {
      myInProcess = false;
    }
  }

  @Override
  public void getArtifact(Artifact artifact, ArtifactRepository repository)
    throws TransferFailedException, ResourceDoesNotExistException {
    getArtifact(artifact, repository, true);
  }

  @Override
  public void getArtifact(Artifact artifact, ArtifactRepository repository, boolean force)
    throws TransferFailedException, ResourceDoesNotExistException {
    if (myInProcess) {
      super.getArtifact(artifact, repository, force);
      return;
    }

    myInProcess = true;
    try {
      if (!takeFromCache(artifact)) {
        try {
          super.getArtifact(artifact, repository, force);
        }
        catch (WagonException ignore) {
        }
        cache(artifact);
      }
      setResolved(artifact);
    }
    finally {
      myInProcess = false;
    }
  }

  @Override
  public void getArtifactMetadata(ArtifactMetadata metadata, ArtifactRepository repository, File destination, String checksumPolicy)
    throws TransferFailedException, ResourceDoesNotExistException {
    // todo use cache here
    super.getArtifactMetadata(metadata, repository, destination, checksumPolicy);
  }

  @Override
  public void getArtifactMetadataFromDeploymentRepository(ArtifactMetadata metadata,
                                                          ArtifactRepository repository,
                                                          File destination,
                                                          String checksumPolicy) throws TransferFailedException,
                                                                                        ResourceDoesNotExistException {
    // todo use cache here
    super.getArtifactMetadataFromDeploymentRepository(metadata, repository, destination, checksumPolicy);
  }

  private boolean takeFromCache(Artifact artifact) {
    CachedValue cached = myCache.get(getKey(artifact));
    if (cached == null) return false;

    boolean fileWasDeleted = cached.wasResolved && !artifact.getFile().exists();
    if (fileWasDeleted) return false; // need to resolve again

    artifact.setResolved(cached.wasResolved);

    return true;
  }

  private void setResolved(Artifact artifact) {
    if (!artifact.isResolved()) myUnresolvedIds.add(new MavenId(artifact));
    if (!myFailOnUnresolved) artifact.setResolved(true);
  }

  private void cache(Artifact artifact) {
    myCache.put(getKey(artifact), new CachedValue(artifact.isResolved()));
  }

  private String getKey(Artifact artifact) {
    return artifact.getGroupId()
           + ":" + artifact.getArtifactId()
           + ":" + artifact.getType()
           + ":" + artifact.getVersion()
           + ":" + artifact.getClassifier();
  }

  private static class CachedValue {
    boolean wasResolved;

    public CachedValue(boolean wasResolved) {
      this.wasResolved = wasResolved;
    }
  }
}
