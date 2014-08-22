package org.jetbrains.plugins.ipnb.editor.panels.code;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.output.StreamCellOutput;

import javax.swing.*;

public class StreamPanel extends IpnbPanel {

  @NotNull private final StreamCellOutput myCell;

  public StreamPanel(@NotNull final StreamCellOutput cell) {
    myCell = cell;
  }

  @Override
  protected JComponent createViewPanel() {   //TODO
    JTextArea textArea = new JTextArea(myCell.getStream());
    textArea.setEditable(false);
    return textArea;
  }
}
