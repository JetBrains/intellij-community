/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.util.ui;

import com.intellij.Patches;

import javax.swing.*;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EventObject;

/**
 * The purpose of this class is to fix two Sun's bugs. There are #4503845 and #4330950.
 * When Sun fixes these bugs then the class should be immediately removed.
 * Also it adds "select row on first right click" functionality.
 *
 * @author Vladimir Kondratyev
 */
public class Table extends JTable {
  private MyCellEditorRemover myEditorRemover;

  public Table() {
    this(new DefaultTableModel());
  }

  public Table(final TableModel model) {
    super(model);
    addMouseListener(new MyMouseListener());
    getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      public void columnMarginChanged(ChangeEvent e) {
        if (cellEditor != null) {
          cellEditor.stopCellEditing();
        }
      }
      public void columnSelectionChanged(ListSelectionEvent e) {}
      public void columnAdded(TableColumnModelEvent e) {}
      public void columnMoved(TableColumnModelEvent e) {}
      public void columnRemoved(TableColumnModelEvent e) {}
    });
    boolean marker = Patches.SUN_BUG_ID_4503845; // Don't remove. It's a marker for find usages
  }

  public void removeNotify() {
    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    keyboardFocusManager.removePropertyChangeListener("focusOwner", myEditorRemover);
    super.removeNotify();
  }

  public boolean editCellAt(final int row, final int column, final EventObject e) {
    if (cellEditor != null && !cellEditor.stopCellEditing()) {
      return false;
    }

    if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
      return false;
    }

    if (!isCellEditable(row, column)) {
      return false;
    }

    if (myEditorRemover == null) {
      final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      myEditorRemover = new MyCellEditorRemover(keyboardFocusManager);
      keyboardFocusManager.addPropertyChangeListener("focusOwner", myEditorRemover);
    }

    final TableCellEditor editor = getCellEditor(row, column);
    if (editor != null && editor.isCellEditable(e)) {
      editorComp = prepareEditor(editor, row, column);
      if (editorComp == null) {
        removeEditor();
        return false;
      }
      editorComp.setBounds(getCellRect(row, column, false));
      add(editorComp);
      editorComp.validate();

      setCellEditor(editor);
      setEditingRow(row);
      setEditingColumn(column);
      editor.addCellEditorListener(this);

      return true;
    }
    return false;
  }

  public void removeEditor() {
    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    keyboardFocusManager.removePropertyChangeListener("focusOwner", myEditorRemover);
    super.removeEditor();
  }

  private final class MyCellEditorRemover implements PropertyChangeListener {
    private final KeyboardFocusManager myFocusManager;

    public MyCellEditorRemover(final KeyboardFocusManager focusManager) {
      myFocusManager = focusManager;
    }

    public void propertyChange(final PropertyChangeEvent e) {
      if (!isEditing()) {
        return;
      }

      Component c = myFocusManager.getFocusOwner();
      while (c != null) {
        if (c == Table.this) {
          // focus remains inside the table
          return;
        }
        else if (c instanceof Window) {
          if (c == SwingUtilities.getWindowAncestor(Table.this)) {
            getCellEditor().stopCellEditing();
          }
          break;
        }
        c = c.getParent();
      }
    }
  }

  public void fixColumnWidthToHeader(final int columnIdx) {
    final TableColumn column = getColumnModel().getColumn(columnIdx);
    final int width = getTableHeader().getFontMetrics(getTableHeader().getFont()).stringWidth(getColumnName(columnIdx)) + 2;
    column.setMinWidth(width);
    column.setMaxWidth(width);
  }

  private final class MyMouseListener extends MouseAdapter {
    public void mousePressed(final MouseEvent e) {
      if (SwingUtilities.isRightMouseButton(e)) {
        final int[] selectedRows = getSelectedRows();
        if (selectedRows.length < 2) {
          final int row = rowAtPoint(e.getPoint());
          if (row != -1) {
            getSelectionModel().setSelectionInterval(row, row);
          }
        }
      }
    }
  }
}