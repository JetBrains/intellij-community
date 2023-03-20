// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomMaven36ArtifactDescriptorReader implements ArtifactDescriptorReader {
  private final ArtifactDescriptorReader myWrapee;
  private final Map<ArtifactDescriptorRequestData, ArtifactDescriptorResultData> descriptorCache = new ConcurrentHashMap<>();

  public CustomMaven36ArtifactDescriptorReader(ArtifactDescriptorReader wrapee) {
    myWrapee = wrapee;
  }

  @Override
  public ArtifactDescriptorResult readArtifactDescriptor(RepositorySystemSession session, ArtifactDescriptorRequest request)
    throws ArtifactDescriptorException {
    return myWrapee.readArtifactDescriptor(session, request);
  }

}
