package com.intellij.debugger.ui.impl.watch;

import java.util.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DescriptorTree {
  private HashMap<NodeDescriptorImpl, List<NodeDescriptorImpl>> myChildrenMap = new HashMap<NodeDescriptorImpl, List<NodeDescriptorImpl>>();
  private List<NodeDescriptorImpl> myRootChildren = new ArrayList<NodeDescriptorImpl>();
  private final boolean myInitial;
  private int myFrameCount = -1;
  private int myFrameIndex = -1;

  public DescriptorTree() {
    this(false);
  }

  public DescriptorTree(boolean isInitial) {
    myInitial = isInitial;
  }

  public boolean frameIdEquals(final int frameCount, final int frameIndex) {
    return myFrameCount == frameCount && myFrameIndex == frameIndex;
  }

  public void setFrameId(final int frameCount, final int frameIndex) {
    myFrameIndex = frameIndex;
    myFrameCount = frameCount;
  }

  public void addChild(NodeDescriptorImpl parent, NodeDescriptorImpl child) {
    List<NodeDescriptorImpl> children;

    if(parent == null) {
      children = myRootChildren;
    }
    else {
      children = myChildrenMap.get(parent);
      if(children == null) {
        children = new ArrayList<NodeDescriptorImpl>();
        myChildrenMap.put(parent, children);
      }
    }
    children.add(child);
    if (myInitial && child instanceof LocalVariableDescriptorImpl) {
      ((LocalVariableDescriptorImpl)child).setNewLocal(false);
    }
  }

  public List<NodeDescriptorImpl> getChildren(NodeDescriptorImpl parent) {
    if(parent == null) return myRootChildren;

    List<NodeDescriptorImpl> children = myChildrenMap.get(parent);
    return children != null ? children : Collections.EMPTY_LIST;
  }

  public void dfst(DFSTWalker walker) {
    dfstImpl(null, myRootChildren, walker);
  }

  private void dfstImpl(NodeDescriptorImpl descriptor, List<NodeDescriptorImpl> children, DFSTWalker walker) {
    if(children != null) {
      for (Iterator<NodeDescriptorImpl> iterator = children.iterator(); iterator.hasNext();) {
        NodeDescriptorImpl child = iterator.next();
        walker.visit(descriptor, child);
        dfstImpl(child, myChildrenMap.get(child), walker);
      }
    }
  }

  public interface DFSTWalker {
    void visit(NodeDescriptorImpl parent, NodeDescriptorImpl child);
  }
}
