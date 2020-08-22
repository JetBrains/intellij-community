// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.generic;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;


class HighlightedSelectorsTable extends TableView<Selector> {

  HighlightedSelectorsTable(@NotNull final FileType valueFileType,
                                   @NotNull final Project project,
                                   @NotNull final List<Selector> selectors) {
    super(new ListTableModel<>(new ColumnInfo[]{
      new ColumnInfo<Selector, String>("Name") {
        @Nullable
        @Override
        public String valueOf(Selector selector) {
          return selector.getName();
        }
      },
      new ColumnInfo<Selector, String>("Path") {

        @Nullable
        @Override
        public String valueOf(Selector selector) {
          return selector.getPath();
        }

        @Override
        public boolean isCellEditable(Selector selector) {
          return true;
        }

        @Override
        public void setValue(Selector selector, String value) {
          selector.setPath(value);
        }

        @Nullable
        @Override
        public TableCellRenderer getRenderer(Selector selector) {
          return new EditorTableCellViewer(valueFileType, project);
        }

        @Nullable
        @Override
        public TableCellEditor getEditor(Selector o) {
          return new EditorTableCellViewer(valueFileType, project);
        }
      }
    }, selectors, 0));
  }

  private static final class EditorTableCellViewer extends AbstractTableCellEditor implements TableCellRenderer {
    private final EditorTextField myEditorField;

    private EditorTableCellViewer(FileType fileType, Project project) {
      myEditorField = new EditorTextField("", project, fileType);
    }

    @Override
    public Object getCellEditorValue() {
      return myEditorField.getText();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myEditorField.setText((String)value);
      return myEditorField;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myEditorField.setText((String)value);
      return myEditorField;
    }
  }
}
