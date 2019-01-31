/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server.embedder;

import gnu.trove.THashMap;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.manager.DefaultWagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.WagonException;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CustomWagonManager extends DefaultWagonManager {
  private UnresolvedArtifactsCollector myUnresolvedCollector;

  private final ThreadLocal<Boolean> myInBatchResolve = new ThreadLocal<Boolean>();
  private final Map<String, Boolean> myResolutionCache = new THashMap<String, Boolean>();

  private final ReentrantReadWriteLock myCacheLock = new ReentrantReadWriteLock();
  private final Lock myCacheReadLock = myCacheLock.readLock();
  private final Lock myCacheWriteLock = myCacheLock.writeLock();

  public void customize(boolean failOnUnresolved) {
    myUnresolvedCollector = new UnresolvedArtifactsCollector(failOnUnresolved);
  }

  public void reset() {
    // todo todo clear cache too??
    myUnresolvedCollector = null;
  }

  public UnresolvedArtifactsCollector getUnresolvedCollector() {
    return myUnresolvedCollector;
  }

  @Override
  public void getArtifact(Artifact artifact, List remoteRepositories) throws TransferFailedException, ResourceDoesNotExistException {
    myInBatchResolve.set(Boolean.TRUE);
    try {
      if (!takeFromCache(artifact)) {
        try {
          super.getArtifact(artifact, remoteRepositories);
        }
        catch (WagonException ignore) {
        }
        cache(artifact);
        myUnresolvedCollector.collectAndSetResolved(artifact);
      }
    }
    finally {
      myInBatchResolve.set(Boolean.FALSE);
    }
  }

  @Override
  public void getArtifact(Artifact artifact, ArtifactRepository repository) throws TransferFailedException, ResourceDoesNotExistException {
    try {
      if (myInBatchResolve.get() == Boolean.TRUE) {
        super.getArtifact(artifact, repository);
        return;
      }

      if (!takeFromCache(artifact)) {
        try {
          super.getArtifact(artifact, repository);
        }
        catch (WagonException ignore) {
        }
        cache(artifact);
        myUnresolvedCollector.collectAndSetResolved(artifact);
      }
    }
    catch (IllegalArgumentException e) {
      if (e.getMessage() != null && e.getMessage().startsWith("Invalid uri")) {
        throw new ResourceDoesNotExistException(e.getMessage(), e);
      }
      throw e;
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
    String key = getKey(artifact);

    Boolean wasResolved;
    myCacheReadLock.lock();
    try {
      wasResolved = myResolutionCache.get(key);
      if (wasResolved == null) return false;
    }
    finally {
      myCacheReadLock.unlock();
    }

    boolean fileWasDeleted = wasResolved && !artifact.getFile().exists();
    if (fileWasDeleted) {
      myCacheWriteLock.lock();
      try {
        myResolutionCache.remove(key);
      }
      finally {
        myCacheWriteLock.unlock();
      }
      return false; // need to resolve again
    }

    artifact.setResolved(wasResolved);

    return true;
  }

  private void cache(Artifact artifact) {
    String key = getKey(artifact);

    myCacheWriteLock.lock();
    try {
      myResolutionCache.put(key, artifact.isResolved());
    }
    finally {
      myCacheWriteLock.unlock();
    }
  }

  private String getKey(Artifact artifact) {
    return artifact.getGroupId()
           + ":" + artifact.getArtifactId()
           + ":" + artifact.getType()
           + ":" + artifact.getVersion()
           + ":" + artifact.getClassifier();
  }
}
