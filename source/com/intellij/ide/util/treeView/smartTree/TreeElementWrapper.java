package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

class TreeElementWrapper extends CachingChildrenTreeNode<TreeElement>{
  public TreeElementWrapper(Project project, TreeElement value, TreeModel treeModel) {
    super(project, value, treeModel);
  }

  public void initChildren() {
    clearChildren();
    TreeElement[] children = getValue().getChildren();
    for (int i = 0; i < children.length; i++) {
      TreeElementWrapper childNode = new TreeElementWrapper(getProject(), children[i], myTreeModel);
      addSubElement(childNode);
    }
  }

  public boolean contains(VirtualFile file) {
    return false;
  }

  public void update(PresentationData presentation) {
    presentation.updateFrom(getValue().getPresentation());
  }

  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}
