package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.ErrorAnalyzer;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class QuickFixManagerImpl extends QuickFixManager<GlassLayer> {
  public QuickFixManagerImpl(final GuiEditor editor, final GlassLayer component) {
    super(editor, component);
    final ComponentTree tree = UIDesignerToolWindowManager.getInstance(editor.getProject()).getComponentTree();
    tree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        hideIntentionHint();
        updateIntentionHintVisibility();
      }
    });
  }

  @NotNull protected ErrorInfo[] getErrorInfos() {
    final ArrayList<RadComponent> list = FormEditingUtil.getSelectedComponents(getEditor());
    if (list.size() != 1) {
      return ErrorInfo.EMPTY_ARRAY;
    }
    return ErrorAnalyzer.getAllErrorsForComponent(list.get(0));
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
