// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.ErrorAnalyzer;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class QuickFixManagerImpl extends QuickFixManager<GlassLayer> {
  public QuickFixManagerImpl(final GuiEditor editor, final GlassLayer component, final JViewport viewPort) {
    super(editor, component, viewPort);
    editor.addComponentSelectionListener(new ComponentSelectionListener() {
      @Override
      public void selectedComponentChanged(GuiEditor source) {
        hideIntentionHint();
        updateIntentionHintVisibility();
      }
    });
  }

  @Override
  @NotNull protected ErrorInfo[] getErrorInfos() {
    final ArrayList<RadComponent> list = FormEditingUtil.getSelectedComponents(getEditor());
    if (list.size() != 1) {
      return ErrorInfo.EMPTY_ARRAY;
    }
    return ErrorAnalyzer.getAllErrorsForComponent(list.get(0));
  }

  @Override
  protected Rectangle getErrorBounds() {
    final ArrayList<RadComponent> list = FormEditingUtil.getSelectedComponents(getEditor());
    if (list.size() != 1) {
      return null;
    }
    RadComponent c = list.get(0);
    return SwingUtilities.convertRectangle(c.getDelegee().getParent(),
                                           c.getBounds(),
                                           getEditor().getGlassLayer());
  }

  @Override
  protected Rectangle getHintClipRect(final JViewport viewPort) {
    // allow some overlap with editor bounds
    Rectangle rc = viewPort.getViewRect();
    rc.grow(4, 4);
    return rc;
  }
}
