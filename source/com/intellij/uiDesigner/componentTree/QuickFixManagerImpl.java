package com.intellij.uiDesigner.componentTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.ErrorAnalizer;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class QuickFixManagerImpl extends QuickFixManager<ComponentTree>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.componentTree.QuickFixManagerImpl");

  public QuickFixManagerImpl(final GuiEditor editor, final ComponentTree componentTree) {
    super(editor, componentTree);
    myComponent.addTreeSelectionListener(new MyTreeSelectionListener());
  }

  protected ErrorInfo getErrorInfo() {
    final RadComponent component = myComponent.getSelectedComponent();
    if(component == null){
      return null;
    }
    return ErrorAnalizer.getErrorForComponent(component);
  }

  public Rectangle getErrorBounds() {
    final TreePath selectionPath = myComponent.getSelectionPath();
    LOG.assertTrue(selectionPath != null);
    return myComponent.getPathBounds(selectionPath);
  }

  private final class MyTreeSelectionListener implements TreeSelectionListener{
    public void valueChanged(final TreeSelectionEvent e) {
      hideIntentionHint();
      updateIntentionHintVisibility();
    }
  }
}