package org.jetbrains.plugins.ipnb.editor.panels;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

import javax.swing.*;

public class IpnbMarkdownPanel extends IpnbEditablePanel<JEditorPane, IpnbMarkdownCell> {

  public IpnbMarkdownPanel(@NotNull final IpnbMarkdownCell cell) {
    super(cell);
    initPanel();
  }

  @Override
  protected String getRawCellText() {
    return myCell.getSourceAsString();
  }

  @Override
  protected JEditorPane createViewPanel() {
    return IpnbUtils.createLatexPane(myCell.getSourceAsString());
  }

  @Override
  public void updateCellView() {
    myViewPanel = IpnbUtils.createLatexPane(myCell.getSourceAsString());
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  protected Object clone() {
    return new IpnbMarkdownPanel((IpnbMarkdownCell)myCell.clone());
  }
}
