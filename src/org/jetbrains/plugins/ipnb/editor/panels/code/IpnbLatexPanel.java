package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.ui.VerticalFlowLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbLatexOutputCell;

import javax.swing.*;
import java.awt.*;

public class IpnbLatexPanel extends IpnbCodeOutputPanel<IpnbLatexOutputCell> {
  public IpnbLatexPanel(@NotNull final IpnbLatexOutputCell cell) {
    super(cell);
    setLayout(new VerticalFlowLayout(FlowLayout.LEFT));
  }

  @Override
  protected JComponent createViewPanel() {
    final JPanel panel = new JPanel();
    final String[] text = myCell.getLatex();
    IpnbUtils.addLatexToPanel(text, panel);
    setBackground(IpnbEditorUtil.getBackground());
    setOpaque(true);
    return panel;
  }
}
