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
package com.intellij.designer.propertyTable;

import com.intellij.designer.inspection.AbstractQuickFixManager;
import com.intellij.designer.model.ErrorInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Arrays;
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

  @NotNull
  @Override
  protected List<ErrorInfo> getErrorInfos() {
    RadPropertyTable component = (RadPropertyTable)myComponent;

    int selectedRow = component.getSelectedRow();
    if (selectedRow < 0 || selectedRow >= component.getRowCount()) {
      return Collections.emptyList();
    }

    ErrorInfo errorInfo = component.getErrorInfoForRow(selectedRow);
    if (errorInfo != null) {
      return Arrays.asList(errorInfo);
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