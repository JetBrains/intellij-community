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
