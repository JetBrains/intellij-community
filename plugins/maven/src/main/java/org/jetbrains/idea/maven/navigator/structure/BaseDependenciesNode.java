// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.model.MavenArtifactNode;
import org.jetbrains.idea.maven.model.MavenArtifactState;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseDependenciesNode extends GroupNode {
  protected final MavenProject myMavenProject;
  private final List<DependencyNode> myChildren = new CopyOnWriteArrayList<>();

  protected BaseDependenciesNode(MavenSimpleNode parent, Project project, MavenProject mavenProject) {
    super(parent, project);
    myMavenProject = mavenProject;
  }

  public MavenProject getMavenProject() {
    return myMavenProject;
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    return myChildren;
  }

  protected void updateChildren(List<MavenArtifactNode> children, MavenProject mavenProject, MavenProjectsStructure mavenProjectsStructure) {
    List<DependencyNode> newNodes = null;
    int validChildCount = 0;

    for (MavenArtifactNode each : children) {
      if (each.getState() != MavenArtifactState.ADDED &&
          each.getState() != MavenArtifactState.CONFLICT &&
          each.getState() != MavenArtifactState.DUPLICATE) {
        continue;
      }

      if (newNodes == null) {
        if (validChildCount < myChildren.size()) {
          DependencyNode currentValidNode = myChildren.get(validChildCount);
          if (currentValidNode.getArtifact().equals(each.getArtifact())
              && currentValidNode.isUnresolved() == each.getArtifact().isFileUnresolved()) {
            if (each.getState() == MavenArtifactState.ADDED) {
              currentValidNode.updateChildren(each.getDependencies(), mavenProject, mavenProjectsStructure);
            }

            validChildCount++;
            continue;
          }
        }

        newNodes = new ArrayList<>(children.size());
        newNodes.addAll(myChildren.subList(0, validChildCount));
      }

      DependencyNode newNode = findOrCreateNodeFor(each, mavenProject, validChildCount);
      newNodes.add(newNode);
      if (each.getState() == MavenArtifactState.ADDED) {
        newNode.updateChildren(each.getDependencies(), mavenProject, mavenProjectsStructure);
      }
      newNode.updateDependency(mavenProjectsStructure);
    }

    if (newNodes == null) {
      if (validChildCount == myChildren.size()) {
        return; // All nodes are valid, child did not changed.
      }

      assert validChildCount < myChildren.size();

      newNodes = new ArrayList<>(myChildren.subList(0, validChildCount));
    }

    myChildren.clear();
    myChildren.addAll(newNodes);
    childrenChanged(mavenProjectsStructure);
  }

  private DependencyNode findOrCreateNodeFor(MavenArtifactNode artifact, MavenProject mavenProject, int from) {
    for (int i = from; i < myChildren.size(); i++) {
      DependencyNode node = myChildren.get(i);
      if (node.getArtifact().equals(artifact.getArtifact()) && node.isUnresolved() == artifact.getArtifact().isFileUnresolved()) {
        return node;
      }
    }
    return new DependencyNode(this, getProject(), artifact, mavenProject, artifact.getArtifact().isFileUnresolved());
  }

  @Override
  String getMenuId() {
    return "Maven.DependencyMenu";
  }
}
