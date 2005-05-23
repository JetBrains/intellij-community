package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class SimpleDisplayKey<T extends NodeDescriptor> implements DisplayKey<T>{
  private final Object myKey;

  public SimpleDisplayKey(Object key) {
    myKey = key;
  }

  public boolean equals(Object o) {
    if(!(o instanceof SimpleDisplayKey)) return false;
    return ((SimpleDisplayKey)o).myKey.equals(myKey);
  }

  public int hashCode() {
    return myKey.hashCode();
  }
}
