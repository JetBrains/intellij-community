package com.intellij.debugger.ui.tree.render;



/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface DescriptorLabelListener {
  DescriptorLabelListener DUMMY_LISTENER = new DescriptorLabelListener() {
    public void labelChanged() {
    }
  };

  void labelChanged();
}
