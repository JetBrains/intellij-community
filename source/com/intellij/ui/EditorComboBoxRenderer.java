package com.intellij.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;

/**
 * @author max
 */
public class EditorComboBoxRenderer extends BasicComboBoxRenderer {
  private EditorComboBoxEditor myEditor;

  public EditorComboBoxRenderer(EditorComboBoxEditor editor) {
    myEditor = editor;
  }

  public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    Font editorFont = myEditor.getEditorComponent().getFont();

    final Component component = super.getListCellRendererComponent(list, value, index,
                                                                   isSelected, cellHasFocus);
    component.setFont(editorFont);
    return component;
  }
}
