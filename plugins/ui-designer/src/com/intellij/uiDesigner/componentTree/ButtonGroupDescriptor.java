// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public Object getElement() {
    return myGroup;
  }

  @Override
  public String toString() {
    return myGroup.getName();
  }
}
