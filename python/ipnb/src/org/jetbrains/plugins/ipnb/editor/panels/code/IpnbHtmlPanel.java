package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbHtmlOutputCell;

import javax.swing.*;

public class IpnbHtmlPanel extends IpnbCodeOutputPanel<IpnbHtmlOutputCell> {

  public IpnbHtmlPanel(@NotNull final IpnbHtmlOutputCell cell, @NotNull final IpnbFilePanel parent,
                       @Nullable IpnbCodePanel ipnbCodePanel) {
    super(cell, parent, ipnbCodePanel);
  }

  @Override
  protected JComponent createViewPanel() {
    final int width = myParent.getWidth();
    return IpnbUtils.createLatexPane(StringUtil.join(myCell.getHtmls(), ""), myParent.getProject(), width);
  }
}
