// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;

/**
 * @author yole
 */
public class ButtonGroupListDescriptor extends NodeDescriptor {
  private final RadButtonGroup[] myButtonGroups;

  public ButtonGroupListDescriptor(final NodeDescriptor parentDescriptor, final RadButtonGroup[] buttonGroups) {
    super(null, parentDescriptor);
    myButtonGroups = buttonGroups;
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public Object getElement() {
    return myButtonGroups;
  }

  @Override public String toString() {
    return UIDesignerBundle.message("node.button.groups");
  }
}
