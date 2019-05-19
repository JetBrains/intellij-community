// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Maven3WorkspaceReader implements WorkspaceReader {

  private final WorkspaceRepository myRepository = new WorkspaceRepository();

  private final MavenWorkspaceMap myWorkspaceMap;

  public Maven3WorkspaceReader(MavenWorkspaceMap workspaceMap) {
    myWorkspaceMap = workspaceMap;
  }

  @Override
  public WorkspaceRepository getRepository() {
    return myRepository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    MavenWorkspaceMap.Data resolved = myWorkspaceMap.findFileAndOriginalId(new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
    if (resolved == null) return null;

    return resolved.getFile(artifact.getExtension());
  }

  private static boolean equals(String s1, String s2) {
    return s1 == null ? s2 == null : s1.equals(s2);
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    List<String> res = new ArrayList<String>();

    for (MavenId id : myWorkspaceMap.getAvailableIds()) {
      if (equals(id.getArtifactId(), artifact.getArtifactId()) && equals(id.getGroupId(), artifact.getGroupId())) {
        String version = id.getVersion();

        if (version != null) {
          res.add(version);
        }
      }
    }

    return res;
  }
}
