// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
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
  @Nullable
  public ComponentDropLocation getAdjacentLocation(Direction direction) {
    return null;
  }
}
