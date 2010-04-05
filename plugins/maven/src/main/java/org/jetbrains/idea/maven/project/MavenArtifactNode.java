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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

public class MavenArtifactNode implements Serializable {
  public enum State { ADDED, EXCLUDED, CONFLICT, DUPLICATE, CYCLE}

  private MavenArtifact myArtifact;
  private State myState;
  private MavenArtifact myRelatedArtifact;

  private String myOriginalScope;

  private String myPremanagedVersion;
  private String myPremanagedScope;

  private List<MavenArtifactNode> myDependencies;

  protected MavenArtifactNode() {
  }

  public MavenArtifactNode(MavenArtifact artifact,
                           State state,
                           MavenArtifact relatedArtifact,
                           String originalScope,
                           String premanagedVersion,
                           String premanagedScope,
                           List<MavenArtifactNode> dependencies) {
    myArtifact = artifact;
    myState = state;
    myRelatedArtifact = relatedArtifact;
    myOriginalScope = originalScope;
    myPremanagedVersion = premanagedVersion;
    myPremanagedScope = premanagedScope;
    myDependencies = dependencies;
  }

  public MavenArtifact getArtifact() {
    return myArtifact;
  }

  public State getState() {
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

  @Override
  public String toString() {
    String result =myArtifact.getDisplayStringWithTypeAndClassifier();
    if (myState != State.ADDED) result += "[" + myState + ":" + myRelatedArtifact.getDisplayStringWithTypeAndClassifier() + "]";
    return result += "->(" + formatNodesList(myDependencies) + ")";
  }

  public static String formatNodesList(List<MavenArtifactNode> nodes) {
    return StringUtil.join(nodes, new Function<MavenArtifactNode, String>() {
      public String fun(MavenArtifactNode each) {
        return each.toString();
      }
    }, ",");
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
