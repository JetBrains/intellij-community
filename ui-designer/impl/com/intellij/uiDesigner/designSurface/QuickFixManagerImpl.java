package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.ErrorAnalyzer;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class QuickFixManagerImpl extends QuickFixManager<GlassLayer> {
  public QuickFixManagerImpl(final GuiEditor editor, final GlassLayer component) {
    super(editor, component);
    getEditor().getComponentTree().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        hideIntentionHint();
        updateIntentionHintVisibility();
      }
    });
  }

  protected ErrorInfo getErrorInfo() {
    final ArrayList<RadComponent> list = FormEditingUtil.getSelectedComponents(getEditor());
    if (list.size() != 1) {
      return null;
    }
    return ErrorAnalyzer.getErrorForComponent(list.get(0));
  }

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
}
