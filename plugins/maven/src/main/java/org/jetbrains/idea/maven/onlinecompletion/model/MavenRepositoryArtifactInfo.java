// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model;


import org.jetbrains.idea.maven.model.MavenCoordinate;

public class MavenRepositoryArtifactInfo implements MavenCoordinate {
  private final String groupId;
  private final String artifactId;
  private final MavenDependencyCompletionItem[] items;

  public MavenRepositoryArtifactInfo(String groupId,
                                     String artifactId,
                                     MavenDependencyCompletionItem[] items) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.items = items;
  }


  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }


  @Override
  public String getVersion() {
    if (items.length < 1) {
      return null;
    }
    return items[0].getVersion();
  }

  public MavenDependencyCompletionItem[] getItems() {
    return items;
  }
}
