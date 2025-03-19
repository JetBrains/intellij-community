// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MavenArtifactNode implements Serializable {
  private final MavenArtifactNode myParent;

  private final MavenArtifact myArtifact;
  private final MavenArtifactState myState;
  private final MavenArtifact myRelatedArtifact;

  private final String myOriginalScope;

  private final String myPremanagedVersion;
  private final String myPremanagedScope;

  private List<MavenArtifactNode> myDependencies;

  public MavenArtifactNode(MavenArtifactNode parent,
                           MavenArtifact artifact,
                           MavenArtifactState state,
                           MavenArtifact relatedArtifact,
                           String originalScope,
                           String premanagedVersion,
                           String premanagedScope) {
    myParent = parent;
    myArtifact = artifact;
    myState = state;
    myRelatedArtifact = relatedArtifact;
    myOriginalScope = originalScope;
    myPremanagedVersion = premanagedVersion;
    myPremanagedScope = premanagedScope;
  }

  public @Nullable MavenArtifactNode getParent() {
    return myParent;
  }


  public MavenArtifact getArtifact() {
    return myArtifact;
  }

  public MavenArtifactState getState() {
    return myState;
  }

  public @Nullable MavenArtifact getRelatedArtifact() {
    return myRelatedArtifact;
  }

  public @Nullable String getOriginalScope() {
    return myOriginalScope;
  }

  public @Nullable String getPremanagedVersion() {
    return myPremanagedVersion;
  }

  public @Nullable String getPremanagedScope() {
    return myPremanagedScope;
  }

  public List<MavenArtifactNode> getDependencies() {
    return myDependencies;
  }

  public void setDependencies(List<MavenArtifactNode> dependencies) {
    myDependencies = new ArrayList<MavenArtifactNode>(dependencies);
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(myArtifact.getDisplayStringWithTypeAndClassifier());
    if (myState != MavenArtifactState.ADDED) {
      result.append('[').append(myState).append(':').append(myRelatedArtifact.getDisplayStringWithTypeAndClassifier()).append(']');
    }
    result.append("->(");
    for (int i = 0; i < myDependencies.size(); i++) {
      if (i > 0) result.append(',');
      result.append(myDependencies.get(i));
    }
    result.append(')');
    return result.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenArtifactNode that = (MavenArtifactNode)o;

    return myArtifact.equals(that.myArtifact);
  }

  @Override
  public int hashCode() {
    return myArtifact.hashCode();
  }
}
