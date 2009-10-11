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

import java.io.Serializable;
import java.util.List;

public class MavenArtifactNode implements Serializable {
  private MavenArtifact myArtifact;
  private List<MavenArtifactNode> myDependencies;

  protected MavenArtifactNode() {
  }

  public MavenArtifactNode(MavenArtifact artifact, List<MavenArtifactNode> dependencies) {
    myArtifact = artifact;
    myDependencies = dependencies;
  }

  public MavenArtifact getArtifact() {
    return myArtifact;
  }

  public List<MavenArtifactNode> getDependencies() {
    return myDependencies;
  }

  @Override
  public String toString() {
    return myArtifact.getDisplayStringWithTypeAndClassifier()
           + "->(" + formatNodesList(myDependencies) + ")";
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

    if (!myArtifact.equals(that.myArtifact)) return false;
    if (!myDependencies.equals(that.myDependencies)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myArtifact.hashCode();
    result = 31 * result + myDependencies.hashCode();
    return result;
  }
}
