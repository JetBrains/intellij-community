/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.model;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
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

  @Nullable
  public MavenArtifactNode getParent() {
    return myParent;
  }


  public MavenArtifact getArtifact() {
    return myArtifact;
  }

  public MavenArtifactState getState() {
    return myState;
  }

  @Nullable
  public MavenArtifact getRelatedArtifact() {
    return myRelatedArtifact;
  }

  @Nullable
  public String getOriginalScope() {
    return myOriginalScope;
  }

  @Nullable
  public String getPremanagedVersion() {
    return myPremanagedVersion;
  }

  @Nullable
  public String getPremanagedScope() {
    return myPremanagedScope;
  }

  public List<MavenArtifactNode> getDependencies() {
    return myDependencies;
  }

  public void setDependencies(List<MavenArtifactNode> dependencies) {
    myDependencies = dependencies;
  }

  @Override
  public String toString() {
    String result = myArtifact.getDisplayStringWithTypeAndClassifier();
    if (myState != MavenArtifactState.ADDED) {
      result += "[" + myState + ":" + myRelatedArtifact.getDisplayStringWithTypeAndClassifier() + "]";
    }
    return result += "->(" + formatNodesList(myDependencies) + ")";
  }

  public static String formatNodesList(List<MavenArtifactNode> nodes) {
    return StringUtil.join(nodes, StringUtil.createToStringFunction(MavenArtifactNode.class), ",");
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
