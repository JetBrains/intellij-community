package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.project.Project;

class TreeElementWrapper extends CachingChildrenTreeNode<TreeElement>{
  public TreeElementWrapper(Project project, TreeElement value, TreeModel treeModel) {
    super(project, value, treeModel);
  }

  public void copyFromNewInstance(final CachingChildrenTreeNode oldInstance) {
  }

  public void initChildren() {
    clearChildren();
    TreeElement[] children = getValue().getChildren();
    for (int i = 0; i < children.length; i++) {
      TreeElementWrapper childNode = new TreeElementWrapper(getProject(), children[i], myTreeModel);
      addSubElement(childNode);
    }
  }

  public void update(PresentationData presentation) {
    if (((StructureViewTreeElement)getValue()).getValue() != null){
      presentation.updateFrom(getValue().getPresentation());
    }
  }

  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}
