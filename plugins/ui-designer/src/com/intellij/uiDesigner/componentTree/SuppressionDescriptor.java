/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

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

  public boolean update() {
    return false;
  }

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
