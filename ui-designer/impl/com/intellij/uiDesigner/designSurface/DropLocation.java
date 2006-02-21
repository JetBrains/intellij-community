/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.core.GridConstraints;

/**
 * @author yole
 */
public interface DropLocation {
  RadContainer getContainer();

  boolean canDrop(ComponentDragObject dragObject);

  void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject);

  void processDrop(GuiEditor editor,
                   RadComponent[] components,
                   GridConstraints[] constraintsToAdjust,
                   ComponentDragObject dragObject);
}
