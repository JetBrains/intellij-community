// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;
import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author yole
 */
public class SuppressionDescriptor extends NodeDescriptor {
  private final RadComponent myTarget;
  private final LwInspectionSuppression myInspectionSuppression;

  public SuppressionDescriptor(final NodeDescriptor parentDescriptor, final RadComponent target, final LwInspectionSuppression inspectionSuppression) {
    super(null, parentDescriptor);
    myTarget = target;
    myInspectionSuppression = inspectionSuppression;
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public Object getElement() {
    return myInspectionSuppression;
  }

  @Override
  public String toString() {
    StringBuilder titleBuilder = new StringBuilder(myInspectionSuppression.getInspectionId());
    if (myTarget != null) {
      titleBuilder.append(" for ");
      titleBuilder.append(myTarget.getDisplayName());
    }
    return titleBuilder.toString();
  }

  public LwInspectionSuppression getSuppression() {
    return myInspectionSuppression;
  }
}
