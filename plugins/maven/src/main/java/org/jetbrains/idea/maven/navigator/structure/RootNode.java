// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApiStatus.Internal
public final class RootNode extends ProjectsGroupNode {
  private final ProfilesNode myProfilesNode;

  RootNode(MavenProjectsStructure structure) {
    super(structure, null);
    myProfilesNode = new ProfilesNode(structure, this);
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    var children = new CopyOnWriteArrayList<MavenSimpleNode>(List.of(myProfilesNode));
    children.addAll(super.doGetChildren());
    return children;
  }

  public void updateProfiles() {
    myProfilesNode.updateProfiles();
  }
}
