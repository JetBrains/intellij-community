package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;

class GroupWrapper extends CachingChildrenTreeNode<Group> {
  public GroupWrapper(Project project, Group value, TreeModel treeModel) {
    super(project, value, treeModel);
  }

  public void copyFromNewInstance(final CachingChildrenTreeNode newInstance) {
    clearChildren();
    setChildren(newInstance.getChildren());
    synchronizeChildren();
  }

  public void update(PresentationData presentation) {
    presentation.updateFrom(getValue().getPresentation());
  }

  public void initChildren() {
    clearChildren();
  }

  protected void performTreeActions() {
  }
}
