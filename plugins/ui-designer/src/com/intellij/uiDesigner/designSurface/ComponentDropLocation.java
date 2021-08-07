// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.Nullable;


public interface ComponentDropLocation {
  enum Direction { LEFT, UP, RIGHT, DOWN }

  RadContainer getContainer();

  boolean canDrop(ComponentDragObject dragObject);

  void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject);

  void processDrop(GuiEditor editor,
                   RadComponent[] components,
                   GridConstraints @Nullable [] constraintsToAdjust,
                   ComponentDragObject dragObject);

  @Nullable
  ComponentDropLocation getAdjacentLocation(Direction direction);
}
