package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbStreamOutputCell;

import javax.swing.*;
import java.awt.event.MouseAdapter;

public class IpnbStreamPanel extends IpnbCodeOutputPanel<IpnbStreamOutputCell> {
  public IpnbStreamPanel(@NotNull final IpnbStreamOutputCell cell, @Nullable MouseAdapter hideOutputListener) {
    super(cell, null, hideOutputListener);
  }

  @Override
  protected JComponent createViewPanel() {
    final JComponent viewPanel = super.createViewPanel();
    if ("stderr".equals(myCell.getStream())) {
      viewPanel.setBackground(MessageType.ERROR.getPopupBackground());
    }
    return viewPanel;
  }
}
