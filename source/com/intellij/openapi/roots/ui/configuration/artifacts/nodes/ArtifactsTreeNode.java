package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.openapi.project.Project;
import com.intellij.ide.util.treeView.NodeDescriptor;

/**
 * @author nik
 */
public abstract class ArtifactsTreeNode extends SimpleNode {
  protected ArtifactsTreeNode(Project project, NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
  }
}
