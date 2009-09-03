/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author yole
 */
public class SuppressionGroupDescriptor extends NodeDescriptor {
  private final LwInspectionSuppression[] myInspectionSuppressions;

  public SuppressionGroupDescriptor(final NodeDescriptor parentDescriptor, final LwInspectionSuppression[] lwInspectionSuppressions) {
    super(null, parentDescriptor);
    myInspectionSuppressions = lwInspectionSuppressions;
  }

  public boolean update() {
    return false;
  }

  public Object getElement() {
    return myInspectionSuppressions;
  }

  @Override public String toString() {
    return UIDesignerBundle.message("node.suppressed.inspections");
  }
}
