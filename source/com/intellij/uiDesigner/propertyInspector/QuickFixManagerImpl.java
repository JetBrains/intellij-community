package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class QuickFixManagerImpl extends QuickFixManager <PropertyInspectorTable>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.propertyInspector.QuickFixManagerImpl");

  public QuickFixManagerImpl(final GuiEditor editor, final PropertyInspectorTable propertyInspectorTable) {
    super(editor, propertyInspectorTable);
    propertyInspectorTable.getSelectionModel().addListSelectionListener(new MyListSelectionListener());
  }

  public Rectangle getErrorBounds() {
    final int selectedRow = myComponent.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myComponent.getRowCount()){
      return null;
    }

    final Rectangle rowRect = myComponent.getCellRect(selectedRow, 0, true);
    LOG.assertTrue(rowRect != null);
    final Rectangle visibleRect = myComponent.getVisibleRect();
    if(visibleRect.intersects(rowRect)){
      return visibleRect.intersection(rowRect);
    }
    else{
      return null;
    }
  }

  public ErrorInfo getErrorInfo() {
    final int selectedRow = myComponent.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myComponent.getRowCount()){
      return null;
    }
    return myComponent.getErrorInfoForRow(selectedRow);
  }

  private final class MyListSelectionListener implements ListSelectionListener{
    public void valueChanged(final ListSelectionEvent e) {
      hideIntentionHint();
      updateIntentionHintVisibility();
    }
  }
}
