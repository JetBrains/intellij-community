package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.ui.VerticalFlowLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

import javax.swing.*;
import java.awt.*;

public class IpnbMarkdownPanel extends IpnbEditablePanel<JPanel, IpnbMarkdownCell> {

  public IpnbMarkdownPanel(@NotNull final IpnbMarkdownCell cell) {
    super(cell);
    initPanel();
  }

  @Override
  protected String getRawCellText() {
    return myCell.getSourceAsString();
  }

  @Override
  protected JPanel createViewPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(FlowLayout.LEFT, false, true));
    updatePanel(panel);
    panel.setBackground(IpnbEditorUtil.getBackground());
    panel.setOpaque(true);
    return panel;
  }

  private void updatePanel(@NotNull final JPanel panel) {
    panel.removeAll();
    IpnbUtils.addLatexToPanel(myCell.getSourceAsString(), panel);
  }


  @Override
  public void updateCellView() {
    updatePanel(myViewPanel);
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  protected Object clone() {
    return new IpnbMarkdownPanel((IpnbMarkdownCell)myCell.clone());
  }
}
