package com.intellij.refactoring.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

/**
 * @author dsl
 */
public class StringTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private Document myDocument;
  private final Project myProject;

  public StringTableCellEditor(final Project project) {
    myProject = project;
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final EditorTextField editorTextField = new EditorTextField((String) value, myProject, StdFileTypes.JAVA) {
            protected boolean shouldHaveBorder() {
              return false;
            }
          };
    myDocument = editorTextField.getDocument();
    return editorTextField;
  }

  public Object getCellEditorValue() {
    return myDocument.getText();
  }
}
