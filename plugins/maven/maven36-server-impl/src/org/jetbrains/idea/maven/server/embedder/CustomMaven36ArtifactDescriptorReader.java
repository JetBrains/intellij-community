// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomMaven36ArtifactDescriptorReader implements ArtifactDescriptorReader, Resetable {
  private final ArtifactDescriptorReader myWrapee;
  private final Map<ArtifactDescriptorRequestData, ArtifactDescriptorResultData> descriptorCache = new ConcurrentHashMap<>();

  public CustomMaven36ArtifactDescriptorReader(ArtifactDescriptorReader wrapee) {
    myWrapee = wrapee;
  }

  protected ArtifactDescriptorResultData doReadArtifactDescriptor(RepositorySystemSession session, ArtifactDescriptorRequest request) {
    try {
      return new ArtifactDescriptorResultData(myWrapee.readArtifactDescriptor(session, request), null);
    }
    catch (ArtifactDescriptorException e) {
      return new ArtifactDescriptorResultData(e.getResult(), e);
    }
  }

  private static @NotNull ArtifactDescriptorResult getResult(ArtifactDescriptorRequest request, ArtifactDescriptorResultData resultData) {
    ArtifactDescriptorResult result = new ArtifactDescriptorResult(request);
    result.setArtifact(resultData.getArtifact());
    result.setRepository(resultData.getRepository());
    result.setExceptions(resultData.getExceptions());
    result.setRelocations(resultData.getRelocations());
    result.setAliases(resultData.getAliases());
    result.setDependencies(resultData.getDependencies());
    result.setManagedDependencies(resultData.getManagedDependencies());
    result.setRepositories(resultData.getRepositories());
    result.setProperties(resultData.getProperties());
    return result;
  }

  @Override
  public ArtifactDescriptorResult readArtifactDescriptor(RepositorySystemSession session, ArtifactDescriptorRequest request)
    throws ArtifactDescriptorException {
    if (null == request) return null;

    ArtifactDescriptorRequestData requestData = new ArtifactDescriptorRequestData(request);

    ArtifactDescriptorResultData resultData = descriptorCache.computeIfAbsent(requestData, rd -> doReadArtifactDescriptor(session, request));

    if (null != resultData.getDescriptorException()) {
      throw resultData.getDescriptorException();
    }

    return getResult(request, resultData);
  }


  @Override
  public void reset() {
    descriptorCache.clear();
  }

}
