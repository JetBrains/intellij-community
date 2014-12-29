package org.jetbrains.plugins.ipnb.editor.panels.code;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;
import java.awt.*;

public class IpnbCodeOutputPanel<K extends IpnbOutputCell> extends IpnbPanel<JComponent, K> {

  public IpnbCodeOutputPanel(@NotNull final K cell) {
    super(cell, new BorderLayout());
    myViewPanel = createViewPanel();
    add(myViewPanel);
  }

  protected JComponent createViewPanel() {
    JTextArea textArea = new JTextArea(myCell.getSourceAsString());
    textArea.setLineWrap(true);
    textArea.setEditable(false);
    textArea.setBackground(IpnbEditorUtil.getBackground());
    return textArea;
  }
}
