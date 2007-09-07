package com.intellij.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;

/**
 * @author max
 */
public class EditorComboBoxRenderer extends BasicComboBoxRenderer {
  private final ComboBoxEditor myEditor;
          
  public EditorComboBoxRenderer(ComboBoxEditor editor) {
    myEditor = editor;
  }

  public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    final Component editorComponent = myEditor.getEditorComponent();
    Font editorFont = editorComponent.getFont();

    final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    component.setFont(editorFont);
    component.setSize(editorComponent.getSize());
    return component;
  }
}
