package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractTreeStructure {
  public abstract Object getRootElement();
  public abstract Object[] getChildElements(Object element);
  @Nullable
  public abstract Object getParentElement(Object element);

  @NotNull
  public abstract NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor);

  public abstract void commit();
  public abstract boolean hasSomethingToCommit();

  public boolean isToBuildChildrenInBackground(Object element){
    return false;
  }
}