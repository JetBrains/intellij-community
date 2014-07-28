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

  private final static String EDITABLE_PANEL = "Editable panel";
  private final static String VIEW_PANEL = "View panel";

  public HeadingPanel(@NotNull final HeadingCell cell) {
    super(new CardLayout());
    myCell = cell;
    final JLabel panel = createViewPanel("<html><h" + myCell.getLevel() + ">" + myCell.getSourceAsString() + "</h" + myCell.getLevel() + "></html>");
    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          setEditing(true);
          final LayoutManager layout = getLayout();
          if (layout instanceof CardLayout) {
            ((CardLayout)layout).show(HeadingPanel.this, EDITABLE_PANEL);
            final Container parent = getParent();
            if (parent instanceof IpnbFilePanel) {
              ((IpnbFilePanel)parent).setSelectedCell(HeadingPanel.this);
              parent.repaint();
            }
          }
        }
      }
    });

    add(panel, VIEW_PANEL);
    final JTextArea textArea = createEditablePanel();
    add(textArea, EDITABLE_PANEL);
  }

  private JTextArea createEditablePanel() {
    final JTextArea textArea = new JTextArea(myCell.getSourceAsString());
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
    return textArea;
  }

  private JLabel createViewPanel(@NotNull final String text) {
    JBLabel label = new JBLabel(text);
    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);
    return label;
  }

}
