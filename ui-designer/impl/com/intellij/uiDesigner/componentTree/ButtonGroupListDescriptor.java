/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author yole
 */
public class ButtonGroupListDescriptor extends NodeDescriptor {
  private final RadButtonGroup[] myButtonGroups;

  public ButtonGroupListDescriptor(final NodeDescriptor parentDescriptor, final RadButtonGroup[] buttonGroups) {
    super(null, parentDescriptor);
    myButtonGroups = buttonGroups;
  }

  public boolean update() {
    return false;
  }

  public Object getElement() {
    return myButtonGroups;
  }

  @Override public String toString() {
    return UIDesignerBundle.message("node.button.groups");
  }
}
