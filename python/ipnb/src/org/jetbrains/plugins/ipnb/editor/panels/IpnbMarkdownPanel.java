package org.jetbrains.plugins.ipnb.editor.panels;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

import javax.swing.*;

public class IpnbMarkdownPanel extends IpnbEditablePanel<JComponent, IpnbMarkdownCell> {

  private final IpnbFilePanel myParent;

  public IpnbMarkdownPanel(@NotNull final IpnbMarkdownCell cell, @NotNull final IpnbFilePanel parent) {
    super(cell);
    myParent = parent;
    initPanel();
  }

  @Override
  protected String getRawCellText() {
    return myCell.getSourceAsString();
  }

  @Override
  protected JComponent createViewPanel() {
    int width = myParent.getWidth();
    return IpnbUtils.createLatexPane(myCell.getSourceAsString(), width);
  }

  @Override
  public void updateCellView() {
    removeAll();
    initPanel();
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  protected Object clone() {
    return new IpnbMarkdownPanel((IpnbMarkdownCell)myCell.clone(), myParent);
  }
}
