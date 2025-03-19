// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class EmptyEditableArea extends ComponentEditableArea {
  public EmptyEditableArea(JComponent component) {
    super(component);
  }

  @Override
  public @Nullable RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
    return null;
  }

  @Override
  public @Nullable InputTool findTargetTool(int x, int y) {
    return null;
  }

  @Override
  public void showSelection(boolean value) {
  }

  @Override
  public ComponentDecorator getRootSelectionDecorator() {
    return null;
  }

  @Override
  public @Nullable EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  @Override
  public FeedbackLayer getFeedbackLayer() {
    return null;
  }

  @Override
  public RadComponent getRootComponent() {
    return null;
  }

  @Override
  public ActionGroup getPopupActions() {
    return null;
  }

  @Override
  public String getPopupPlace() {
    return null;
  }
}