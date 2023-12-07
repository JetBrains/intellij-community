// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.Nullable;


public final class NoDropLocation implements ComponentDropLocation {
  public static final NoDropLocation INSTANCE = new NoDropLocation();

  private NoDropLocation() {
  }

  @Override
  public RadContainer getContainer() {
    return null;
  }

  @Override
  public boolean canDrop(ComponentDragObject dragObject) {
    return false;
  }

  @Override
  public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
  }

  @Override
  public void processDrop(GuiEditor editor,
                          RadComponent[] components,
                          GridConstraints[] constraintsToAdjust,
                          ComponentDragObject dragObject) {
  }

  @Override
  public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
    return null;
  }
}
