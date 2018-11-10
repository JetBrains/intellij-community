// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;

/**
 * @author yole
 */
public class SuppressionGroupDescriptor extends NodeDescriptor {
  private final LwInspectionSuppression[] myInspectionSuppressions;

  public SuppressionGroupDescriptor(final NodeDescriptor parentDescriptor, final LwInspectionSuppression[] lwInspectionSuppressions) {
    super(null, parentDescriptor);
    myInspectionSuppressions = lwInspectionSuppressions;
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public Object getElement() {
    return myInspectionSuppressions;
  }

  @Override public String toString() {
    return UIDesignerBundle.message("node.suppressed.inspections");
  }
}
