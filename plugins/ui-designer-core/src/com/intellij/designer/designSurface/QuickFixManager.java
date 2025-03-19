// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.designSurface;

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
  public QuickFixManager(DesignerEditorPanel designer, JComponent component, JViewport viewPort) {
    super(designer, component, viewPort);
    designer.getSurfaceArea().addSelectionListener(this);
  }

  @Override
  public void selectionChanged(EditableArea area) {
    hideHint();
    updateHintVisibility();
  }

  @Override
  protected @NotNull List<ErrorInfo> getErrorInfos() {
    List<RadComponent> selection = myDesigner.getSurfaceArea().getSelection();
    if (selection.size() == 1) {
      return RadComponent.getError(selection.get(0));
    }
    return Collections.emptyList();
  }

  @Override
  protected Rectangle getErrorBounds() {
    List<RadComponent> selection = myDesigner.getSurfaceArea().getSelection();
    if (selection.size() == 1) {
      Rectangle bounds = selection.get(0).getBounds(myComponent);
      bounds.x -= AllIcons.Actions.IntentionBulb.getIconWidth() - 5;
      return bounds;
    }

    return null;
  }

  @Override
  protected Rectangle getHintClipRect() {
    Rectangle clipRect = super.getHintClipRect();
    clipRect.grow(4, 4);
    return clipRect;
  }
}
