/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  @Override
  protected List<ErrorInfo> getErrorInfos() {
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
