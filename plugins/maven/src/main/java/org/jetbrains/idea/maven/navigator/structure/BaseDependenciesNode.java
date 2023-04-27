// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.model.MavenArtifactNode;
import org.jetbrains.idea.maven.model.MavenArtifactState;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This implementation creates children lazily to reduce memory usage.
 * {@link #doGetChildren()} method is expected to be called only when the node is displayed.
 * Visibility and error level are calculated without creating children.
 */
abstract class BaseDependenciesNode extends GroupNode {
  protected final MavenProject myMavenProject;
  private final List<DependencyNode> myChildren = new CopyOnWriteArrayList<>();
  private final AtomicReference<ChildrenUpdate> myChildrenUpdate = new AtomicReference<>();

  protected BaseDependenciesNode(MavenProjectsStructure structure,
                                 MavenSimpleNode parent,
                                 MavenProject mavenProject) {
    super(structure, parent);
    myMavenProject = mavenProject;
  }

  public MavenProject getMavenProject() {
    return myMavenProject;
  }

  @Override
  public boolean isVisible() {
    var childrenUpdate = myChildrenUpdate.get();
    if (null != childrenUpdate) {
      return !childrenUpdate.children.isEmpty();
    }
    return !myChildren.isEmpty();
  }

  private static boolean hasUnresolvedFile(MavenArtifactNode mavenArtifactNode) {
    if (mavenArtifactNode.getArtifact().isFileUnresolved()) {
      return true;
    }
    for (var dependency : mavenArtifactNode.getDependencies()) {
      if (hasUnresolvedFile(dependency)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public MavenProjectsStructure.ErrorLevel getChildrenErrorLevel() {
    var childrenUpdate = myChildrenUpdate.get();
    if (null != childrenUpdate) {
      if (ContainerUtil.exists(childrenUpdate.children, mavenArtifactNode -> hasUnresolvedFile(mavenArtifactNode))) {
        return MavenProjectsStructure.ErrorLevel.ERROR;
      }
      return MavenProjectsStructure.ErrorLevel.NONE;
    }
    return super.getChildrenErrorLevel();
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    var childrenUpdate = myChildrenUpdate.getAndSet(null);
    if (null != childrenUpdate) {
      doUpdateChildren(childrenUpdate);
    }
    return myChildren;
  }

  private record ChildrenUpdate(List<MavenArtifactNode> children, MavenProject mavenProject) {
  }

  protected void updateChildren(List<MavenArtifactNode> children, MavenProject mavenProject) {
    myChildrenUpdate.set(new ChildrenUpdate(children, mavenProject));
    childrenChanged();
  }

  private void doUpdateChildren(ChildrenUpdate childrenUpdate) {
    List<MavenArtifactNode> children = childrenUpdate.children();
    MavenProject mavenProject = childrenUpdate.mavenProject();

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
              currentValidNode.updateChildren(each.getDependencies(), mavenProject);
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
        newNode.updateChildren(each.getDependencies(), mavenProject);
      }
      newNode.updateDependency();
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
    childrenChanged();
  }

  private DependencyNode findOrCreateNodeFor(MavenArtifactNode artifact, MavenProject mavenProject, int from) {
    for (int i = from; i < myChildren.size(); i++) {
      DependencyNode node = myChildren.get(i);
      if (node.getArtifact().equals(artifact.getArtifact()) && node.isUnresolved() == artifact.getArtifact().isFileUnresolved()) {
        return node;
      }
    }
    return new DependencyNode(myMavenProjectsStructure, this, artifact, mavenProject, artifact.getArtifact().isFileUnresolved());
  }

  @Override
  String getMenuId() {
    return "Maven.DependencyMenu";
  }
}
