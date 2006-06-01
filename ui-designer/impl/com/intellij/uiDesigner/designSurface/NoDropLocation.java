/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class NoDropLocation implements DropLocation {
  public static final NoDropLocation INSTANCE = new NoDropLocation();

  private NoDropLocation() {
  }

  public RadContainer getContainer() {
    return null;
  }

  public boolean canDrop(ComponentDragObject dragObject) {
    return false;
  }

  public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
  }

  public void processDrop(GuiEditor editor,
                          RadComponent[] components,
                          GridConstraints[] constraintsToAdjust,
                          ComponentDragObject dragObject) {
  }

  @Nullable
  public DropLocation getAdjacentLocation(Direction direction) {
    return null;
  }
}
