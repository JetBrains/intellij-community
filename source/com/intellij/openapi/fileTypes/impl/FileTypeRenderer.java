package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;

import javax.swing.*;
import java.awt.*;

public class FileTypeRenderer extends DefaultListCellRenderer {
  public FileTypeRenderer() {
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    FileType type = (FileType)value;
    setIcon(type.getIcon());
    String description = type.getDescription();
    if (description != null) {
      setText(description);
    }
    return this;
  }

  public Dimension getPreferredSize() {
    return new Dimension(0, 20);
  }
}
