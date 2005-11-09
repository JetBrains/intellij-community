package com.intellij.uiDesigner.componentTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.uiDesigner.ErrorAnalyzer;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;

import org.jetbrains.annotations.Nullable;

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

  @Nullable
  protected ErrorInfo getErrorInfo() {
    final RadComponent component = myComponent.getSelectedComponent();
    if(component == null){
      return null;
    }
    return ErrorAnalyzer.getErrorForComponent(component);
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

      ErrorInfo errorInfo = getErrorInfo();
      final StatusBar statusBar = WindowManager.getInstance().getStatusBar(getEditor().getProject());
      if (errorInfo != null && errorInfo.myDescription != null) {
        statusBar.setInfo(errorInfo.myDescription);
      }
      else {
        statusBar.setInfo("");
      }
    }
  }
}