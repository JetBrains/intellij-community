package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
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
    IpnbUtils.addLatexToPanel(StringUtil.join(myCell.getLatex()), panel);
    setBackground(IpnbEditorUtil.getBackground());
    panel.setBackground(IpnbEditorUtil.getBackground());
    panel.setOpaque(true);
    setOpaque(true);
    return panel;
  }
}
