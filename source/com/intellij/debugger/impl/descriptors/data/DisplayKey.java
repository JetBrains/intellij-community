package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface DisplayKey <T extends NodeDescriptorImpl> extends DescriptorKey<T>{
  public abstract boolean equals(Object object);

  public abstract int hashCode();
}
