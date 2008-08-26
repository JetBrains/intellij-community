package org.jetbrains.plugins.ruby.testing.sm.runner.ui.statistics;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public interface ColoredRenderer {
  void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column);
}
