package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ui.treeStructure.SimpleNode;

/**
 * @author nik
 */
public abstract class ArtifactsTreeNode extends SimpleNode {
  private final TreeNodePresentation myPresentation;
  protected final PackagingEditorContext myContext;

  protected ArtifactsTreeNode(PackagingEditorContext context, NodeDescriptor parentDescriptor, final TreeNodePresentation presentation) {
    super(context.getProject(), parentDescriptor);
    myContext = context;
    myPresentation = presentation;
  }

  @Override
  protected void update(PresentationData presentation) {
    myPresentation.render(presentation);
    presentation.setTooltip(myPresentation.getTooltipText());
  }

  public TreeNodePresentation getElementPresentation() {
    return myPresentation;
  }

  @Override
  public int getWeight() {
    return myPresentation.getWeight();
  }

  @Override
  public String getName() {
    return myPresentation.getPresentableName();
  }
}
