/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;

/**
 * @author yole
 */
public class ButtonGroupDescriptor extends NodeDescriptor {
  private final RadButtonGroup myGroup;

  public ButtonGroupDescriptor(final NodeDescriptor parentDescriptor, final RadButtonGroup group) {
    super(null, parentDescriptor);
    myGroup = group;
  }

  public boolean update() {
    return false;
  }

  public Object getElement() {
    return myGroup;
  }

  @Override
  public String toString() {
    return myGroup.getName();
  }
}
