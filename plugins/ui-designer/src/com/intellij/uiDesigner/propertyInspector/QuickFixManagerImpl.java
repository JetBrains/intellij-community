// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

final class QuickFixManagerImpl extends QuickFixManager <PropertyInspectorTable>{
  private static final Logger LOG = Logger.getInstance(QuickFixManagerImpl.class);

  QuickFixManagerImpl(final GuiEditor editor, final PropertyInspectorTable propertyInspectorTable, final JViewport viewPort) {
    super(editor, propertyInspectorTable, viewPort);
    propertyInspectorTable.getSelectionModel().addListSelectionListener(new MyListSelectionListener());
  }

  @Override
  public @Nullable Rectangle getErrorBounds() {
    final int selectedRow = myComponent.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myComponent.getRowCount()){
      return null;
    }

    final Rectangle rowRect = myComponent.getCellRect(selectedRow, 0, true);
    final Rectangle visibleRect = myComponent.getVisibleRect();
    if(visibleRect.intersects(rowRect)){
      return visibleRect.intersection(rowRect);
    }
    else{
      return null;
    }
  }

  @Override
  public ErrorInfo @NotNull [] getErrorInfos() {
    final int selectedRow = myComponent.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myComponent.getRowCount()){
      return ErrorInfo.EMPTY_ARRAY;
    }
    final ErrorInfo info = myComponent.getErrorInfoForRow(selectedRow);
    if (info != null) {
      return new ErrorInfo[] { info };
    }
    return ErrorInfo.EMPTY_ARRAY;
  }

  private final class MyListSelectionListener implements ListSelectionListener{
    @Override
    public void valueChanged(final ListSelectionEvent e) {
      hideIntentionHint();
      updateIntentionHintVisibility();
    }
  }
}
