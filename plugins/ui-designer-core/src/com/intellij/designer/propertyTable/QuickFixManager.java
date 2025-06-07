// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable;

import com.intellij.designer.inspection.AbstractQuickFixManager;
import com.intellij.designer.model.ErrorInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class QuickFixManager extends AbstractQuickFixManager implements ListSelectionListener {
  public QuickFixManager(PropertyTable component, JViewport viewPort) {
    super(null, component, viewPort);
    component.getSelectionModel().addListSelectionListener(this);
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    hideHint();
    updateHintVisibility();
  }

  @Override
  protected @NotNull List<ErrorInfo> getErrorInfos() {
    RadPropertyTable component = (RadPropertyTable)myComponent;

    int selectedRow = component.getSelectedRow();
    if (selectedRow < 0 || selectedRow >= component.getRowCount()) {
      return Collections.emptyList();
    }

    ErrorInfo errorInfo = component.getErrorInfoForRow(selectedRow);
    if (errorInfo != null) {
      return Collections.singletonList(errorInfo);
    }
    return Collections.emptyList();
  }

  @Override
  protected Rectangle getErrorBounds() {
    RadPropertyTable component = (RadPropertyTable)myComponent;

    int selectedRow = component.getSelectedRow();
    if (selectedRow < 0 || selectedRow >= component.getRowCount()) {
      return null;
    }

    Rectangle rowRect = component.getCellRect(selectedRow, 0, true);
    Rectangle visibleRect = myComponent.getVisibleRect();
    if (visibleRect.intersects(rowRect)) {
      return visibleRect.intersection(rowRect);
    }
    return null;
  }
}