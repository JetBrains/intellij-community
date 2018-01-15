package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

class RunAnythingMore extends JPanel {
  static final RunAnythingMore instance = new RunAnythingMore();
  final JLabel label = new JLabel(" load more ...");

  private RunAnythingMore() {
    super(new BorderLayout());
    add(label, BorderLayout.CENTER);
  }

  static RunAnythingMore get(boolean isSelected) {
    instance.setBackground(UIUtil.getListBackground(isSelected));
    instance.label.setForeground(UIUtil.getLabelDisabledForeground());
    instance.label.setFont(RunAnythingUtil.getTitleFont());
    instance.label.setBackground(UIUtil.getListBackground(isSelected));
    return instance;
  }
}
