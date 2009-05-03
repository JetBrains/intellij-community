package org.jetbrains.idea.maven.embedder;

import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.manager.DefaultWagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.WagonException;
import org.jetbrains.idea.maven.utils.MavenId;

import java.io.File;
import java.util.List;
import java.util.Set;

public class CustomWagonManager extends DefaultWagonManager {
  private boolean myCustomized;
  private boolean myUseRemoteRepository;
  private boolean myFailOnUnresolved;

  private Set<MavenId> myUnresolvedIds = new THashSet<MavenId>();
  private int myOpenCount;
  private boolean myInProcess = false;

  public void customize(boolean useRemoteRepository, boolean failOnUnresolved) {
    myCustomized = true;
    myUseRemoteRepository = useRemoteRepository;
    myFailOnUnresolved = failOnUnresolved;
  }

  public void reset() {
    myCustomized = false;
  }

  public Set<MavenId> retrieveUnresolvedIds() {
    Set<MavenId> result = myUnresolvedIds;
    myUnresolvedIds = new THashSet<MavenId>();
    return result;
  }

  @Override
  public void getArtifact(Artifact artifact, List remoteRepositories) throws TransferFailedException, ResourceDoesNotExistException {
    if (!myCustomized || myInProcess) {
      super.getArtifact(artifact, remoteRepositories);
      return;
    }

    myInProcess = true;
    try {
      if (isOpen()) {
        try {
          super.getArtifact(artifact, remoteRepositories);
        }
        catch (WagonException ignore) {
        }
      }
      postResolve(artifact);
    }
    finally {
      myInProcess = false;
    }
  }

  @Override
  public void getArtifact(Artifact artifact, List remoteRepositories, boolean force)
    throws TransferFailedException, ResourceDoesNotExistException {
    if (!myCustomized || myInProcess) {
      super.getArtifact(artifact, remoteRepositories, force);
      return;
    }

    myInProcess = true;
    try {
      if (isOpen()) {
        try {
          super.getArtifact(artifact, remoteRepositories, force);
        }
        catch (WagonException ignore) {
        }
      }
      postResolve(artifact);
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
    if (!myCustomized || myInProcess) {
      super.getArtifact(artifact, repository, force);
      return;
    }

    myInProcess = true;
    try {
      if (isOpen()) {
        try {
          super.getArtifact(artifact, repository, force);
        }
        catch (WagonException ignore) {
        }
      }
      postResolve(artifact);
    }
    finally {
      myInProcess = false;
    }
  }

  @Override
  public void getArtifactMetadata(ArtifactMetadata metadata, ArtifactRepository repository, File destination, String checksumPolicy)
    throws TransferFailedException, ResourceDoesNotExistException {

    if (!myCustomized || isOpen()) {
      super.getArtifactMetadata(metadata, repository, destination, checksumPolicy);
    }
  }

  @Override
  public void getArtifactMetadataFromDeploymentRepository(ArtifactMetadata metadata,
                                                          ArtifactRepository repository,
                                                          File destination,
                                                          String checksumPolicy)
    throws TransferFailedException, ResourceDoesNotExistException {
    if (!myCustomized || isOpen()) {
      super.getArtifactMetadataFromDeploymentRepository(metadata, repository, destination, checksumPolicy);
    }
  }

  private void postResolve(Artifact artifact) {
    if (!artifact.isResolved()) myUnresolvedIds.add(new MavenId(artifact));
    if (!myFailOnUnresolved) artifact.setResolved(true);
  }

  private boolean isOpen() {
    return myUseRemoteRepository || myOpenCount > 0;
  }

  public void open() {
    myOpenCount++;
  }

  public void close() {
    assert myOpenCount > 0;
    myOpenCount--;
  }
}
