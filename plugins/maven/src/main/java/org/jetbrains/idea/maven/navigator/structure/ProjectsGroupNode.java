// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import icons.MavenIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApiStatus.Internal
public abstract class ProjectsGroupNode extends GroupNode {
  private final List<ProjectNode> myProjectNodes = new CopyOnWriteArrayList<>();

  ProjectsGroupNode(MavenProjectsStructure structure, MavenSimpleNode parent) {
    super(structure, parent);
    getTemplatePresentation().setIcon(MavenIcons.ModulesClosed);
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    return myProjectNodes;
  }

  @TestOnly
  public List<ProjectNode> getProjectNodesInTests() {
    return myProjectNodes;
  }

  protected void add(ProjectNode projectNode) {
    projectNode.setParent(this);
    insertSorted(myProjectNodes, projectNode);

    childrenChanged();
  }

  public void remove(ProjectNode projectNode) {
    projectNode.setParent(null);
    myProjectNodes.remove(projectNode);

    childrenChanged();
  }

  public void sortProjects() {
    sort(myProjectNodes);
    childrenChanged();
  }
}
