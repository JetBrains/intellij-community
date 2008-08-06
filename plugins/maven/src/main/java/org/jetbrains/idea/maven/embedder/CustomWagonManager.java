package org.jetbrains.idea.maven.embedder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.manager.DefaultWagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.WagonException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomWagonManager extends DefaultWagonManager {
  public static final String IS_ONLINE = "IS_ONLINE";

  private boolean isOnline;

  private Set<MavenId> myUnresolvedIds = new HashSet<MavenId>();
  private boolean isOpen;
  private boolean isInProcess = false;

  @Override
  public void contextualize(Context context) throws ContextException {
    super.contextualize(context);
    isOnline = (Boolean)context.get(IS_ONLINE);
  }

  public Set<MavenId> getUnresolvedIds() {
    return myUnresolvedIds;
  }

  public void resetUnresolvedArtifacts() {
    myUnresolvedIds = new HashSet<MavenId>();
  }

  @Override
  public void getArtifact(Artifact artifact, List remoteRepositories) throws TransferFailedException, ResourceDoesNotExistException {
    if (isInProcess) {
      super.getArtifact(artifact, remoteRepositories);
      return;
    }

    isInProcess = true;
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
      isInProcess = false;
    }
  }

  @Override
  public void getArtifact(Artifact artifact, List remoteRepositories, boolean force)
    throws TransferFailedException, ResourceDoesNotExistException {
    if (isInProcess) {
      super.getArtifact(artifact, remoteRepositories, force);
      return;
    }

    isInProcess = true;
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
      isInProcess = false;
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
    if (isInProcess) {
      super.getArtifact(artifact, repository, force);
      return;
    }

    isInProcess = true;
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
      isInProcess = false;
    }
  }

  private void postResolve(Artifact artifact) {
    if (!artifact.isResolved()) myUnresolvedIds.add(new MavenId(artifact));
    artifact.setResolved(true);
  }

  @Override
  public void getArtifactMetadata(ArtifactMetadata metadata, ArtifactRepository repository, File destination, String checksumPolicy)
    throws TransferFailedException, ResourceDoesNotExistException {
    if (isOpen()) {
      super.getArtifactMetadata(metadata, repository, destination, checksumPolicy);
    }
  }

  @Override
  public void getArtifactMetadataFromDeploymentRepository(ArtifactMetadata metadata,
                                                          ArtifactRepository repository,
                                                          File destination,
                                                          String checksumPolicy)
    throws TransferFailedException, ResourceDoesNotExistException {
    if (isOpen()) {
      super.getArtifactMetadataFromDeploymentRepository(metadata, repository, destination, checksumPolicy);
    }
  }

  private boolean isOpen() {
    return isOnline || isOpen;
  }

  public void open() {
    isOpen = true;
  }

  public void close() {
    isOpen = false;
  }
}
