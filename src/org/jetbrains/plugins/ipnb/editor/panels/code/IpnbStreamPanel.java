package org.jetbrains.plugins.ipnb.editor.panels.code;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbStreamOutputCell;

import javax.swing.*;

public class IpnbStreamPanel extends IpnbCodeOutputPanel<IpnbStreamOutputCell> {
  public IpnbStreamPanel(@NotNull final IpnbStreamOutputCell cell) {
    super(cell);
  }

  @Override
  protected JComponent createViewPanel() {
    JTextArea textArea = new JTextArea(myCell.getSourceAsString());
    textArea.setEditable(false);
    return textArea;
  }
}
