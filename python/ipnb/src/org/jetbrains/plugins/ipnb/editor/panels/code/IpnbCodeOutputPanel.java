package org.jetbrains.plugins.ipnb.editor.panels.code;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;

public class IpnbCodeOutputPanel<K extends IpnbOutputCell> extends IpnbPanel<JComponent, K> {

  public IpnbCodeOutputPanel(@NotNull final K cell) {
    super(cell);
    myViewPanel = createViewPanel();
    add(myViewPanel);
  }

  protected JComponent createViewPanel() {
    JTextArea textArea = new JTextArea(myCell.getSourceAsString());
    textArea.setEditable(false);
    return textArea;
  }
}
