package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.impl.descriptors.data.DescriptorData;
import com.intellij.debugger.impl.descriptors.data.DescriptorKey;
import com.intellij.util.containers.*;

import java.util.*;
import java.util.HashMap;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class MarkedDescriptorTree {
  private HashMap<NodeDescriptorImpl, Map<DescriptorKey, NodeDescriptorImpl>> myChildrenMap = new HashMap<NodeDescriptorImpl, Map<DescriptorKey, NodeDescriptorImpl>>();
  private Map<DescriptorKey, NodeDescriptorImpl> myRootChildren = new com.intellij.util.containers.HashMap<DescriptorKey, NodeDescriptorImpl>();

  public <T extends NodeDescriptorImpl> void addChild(NodeDescriptorImpl parent, T child, DescriptorKey<T> key) {
    Map<DescriptorKey, NodeDescriptorImpl> children;

    if(parent == null) {
      children = myRootChildren;
    }
    else {
      children = myChildrenMap.get(parent);
      if(children == null) {
        children = new com.intellij.util.containers.HashMap<DescriptorKey, NodeDescriptorImpl>();
        myChildrenMap.put(parent, children);
      }
    }
    children.put(key, child);
  }

  public <T extends NodeDescriptorImpl> T getChild(NodeDescriptorImpl parent, DescriptorKey<T> key) {
    if(parent == null) return (T)myRootChildren.get(key);
    Map<DescriptorKey, NodeDescriptorImpl> map = myChildrenMap.get(parent);
    return (T)(map != null ? map.get(key) : null);
  }

  public void clear() {
    myChildrenMap.clear();
  }
}
