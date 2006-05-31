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
      if (myTarget.getBinding() != null) {
        titleBuilder.append(myTarget.getBinding());
      }
      else {
        final String className = myTarget.getComponentClassName();
        int pos = className.lastIndexOf('.');
        if (pos < 0) {
          titleBuilder.append(className);
        }
        else {
          titleBuilder.append(className.substring(pos + 1).replace('$', '.'));
        }
        titleBuilder.append(" ").append(ComponentTree.getComponentTitle(myTarget));
      }
    }
    return titleBuilder.toString();
  }

  public LwInspectionSuppression getSuppression() {
    return myInspectionSuppression;
  }
}
