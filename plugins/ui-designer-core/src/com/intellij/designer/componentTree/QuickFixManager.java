// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.componentTree;

import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.inspection.AbstractQuickFixManager;
import com.intellij.designer.model.ErrorInfo;
import com.intellij.designer.model.RadComponent;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class QuickFixManager extends AbstractQuickFixManager implements ComponentSelectionListener {
  private EditableArea myArea;

  public QuickFixManager(JComponent component, JViewport viewPort) {
    super(null, component, viewPort);
  }

  public void setEditableArea(EditableArea area) {
    myArea = area;
    area.addSelectionListener(this);
  }

  @Override
  public void selectionChanged(EditableArea area) {
    hideHint();
    updateHintVisibility();
  }

  @Override
  protected @NotNull List<ErrorInfo> getErrorInfos() {
    List<RadComponent> selection = myArea.getSelection();
    if (selection.size() == 1) {
      return RadComponent.getError(selection.get(0));
    }
    return Collections.emptyList();
  }

  @Override
  protected Rectangle getErrorBounds() {
    ComponentTree component = (ComponentTree)myComponent;
    Rectangle bounds = component.getPathBounds(component.getSelectionPath());
    if (bounds != null) {
      bounds.x += AllIcons.Actions.IntentionBulb.getIconWidth();
    }
    return bounds;
  }
}
