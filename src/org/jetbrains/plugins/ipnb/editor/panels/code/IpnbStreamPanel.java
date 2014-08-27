package org.jetbrains.plugins.ipnb.editor.panels.code;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbStreamOutputCell;

import javax.swing.*;

public class IpnbStreamPanel extends IpnbPanel {

  @NotNull private final IpnbStreamOutputCell myCell;

  public IpnbStreamPanel(@NotNull final IpnbStreamOutputCell cell) {
    myCell = cell;
    myViewPanel = createViewPanel();
    add(myViewPanel);
  }

  @Override
  protected JComponent createViewPanel() {
    JTextArea textArea = new JTextArea(myCell.getSourceAsString());
    textArea.setEditable(false);
    return textArea;
  }
}
