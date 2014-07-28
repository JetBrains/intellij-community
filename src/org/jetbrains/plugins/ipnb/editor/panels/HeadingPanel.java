package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.HeadingCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HeadingPanel extends IpnbPanel {
  private final HeadingCell myCell;

  public HeadingPanel(@NotNull final HeadingCell cell) {
    myCell = cell;
    final JLabel panel = createPanel();
    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          setEditing(true);
          updatePanel(myCell.getSourceAsString());
          final Container parent = getParent();
          if (parent instanceof IpnbFilePanel) {
            ((IpnbFilePanel)parent).setSelectedCell(HeadingPanel.this);
            repaint();
            parent.repaint();
          }
        }
      }
    });
    add(panel, BorderLayout.CENTER);
  }

  private JLabel createPanel() {
    final String text = "<html><h" + myCell.getLevel() + ">" + myCell.getSourceAsString() + "</h" + myCell.getLevel() + "></html>";
    JBLabel label = new JBLabel(text);
    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);
    return label;
  }

  private void updatePanel(@NotNull final String text) {
    removeAll();
    final JTextArea textArea = new JTextArea(text);
    textArea.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1) {
          final Container parent = getParent();
          if (parent instanceof IpnbFilePanel) {
            ((IpnbFilePanel)parent).setSelectedCell(HeadingPanel.this);
            parent.repaint();
          }
        }
      }
    });

    add(textArea, BorderLayout.CENTER);
  }
}
