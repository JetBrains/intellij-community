package com.intellij.ide.util.treeView;

public abstract class AbstractTreeStructure {
  public abstract Object getRootElement();
  public abstract Object[] getChildElements(Object element);
  public abstract Object getParentElement(Object element);

  public abstract NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor);

  public abstract void commit();
  public abstract boolean hasSomethingToCommit();

  public boolean isToBuildChildrenInBackground(Object element){
    return false;
  }
}