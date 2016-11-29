/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  public HighlightedSelectorsTable(@NotNull final FileType valueFileType,
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

  private static class EditorTableCellViewer extends AbstractTableCellEditor implements TableCellRenderer {
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
