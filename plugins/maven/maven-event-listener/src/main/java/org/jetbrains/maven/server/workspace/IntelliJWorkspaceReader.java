// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.maven.server.workspace;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Component(role = WorkspaceReader.class, hint = "ide")
public class IntelliJWorkspaceReader implements WorkspaceReader {

  private final WorkspaceRepository myWorkspaceRepository;

  public IntelliJWorkspaceReader() {
    myWorkspaceRepository = new WorkspaceRepository("ide", getClass());
  }

  @Override
  public int hashCode() {
    return 42;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof IntelliJWorkspaceReader;
  }

  @Override
  public WorkspaceRepository getRepository() {
    return myWorkspaceRepository;
  }

  @SuppressWarnings("IO_FILE_USAGE")
  @Override
  public File findArtifact(Artifact artifact) {
    return MavenModuleMap.getInstance().findArtifact(artifact);
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    return Collections.emptyList();
  }
}
