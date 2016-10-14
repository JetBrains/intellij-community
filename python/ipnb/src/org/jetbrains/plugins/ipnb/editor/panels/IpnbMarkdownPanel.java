package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbMergeCellAboveAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbMergeCellBelowAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbSplitCellAction;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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

  @Override
  public void addRightClickMenu() {
    myViewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final DefaultActionGroup group = new DefaultActionGroup(new IpnbMergeCellAboveAction(), new IpnbMergeCellBelowAction());
          createClickMenu(e.getLocationOnScreen(), group);
        }
      }
    });
    myEditablePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final DefaultActionGroup group = new DefaultActionGroup(new IpnbSplitCellAction());
          createClickMenu(e.getLocationOnScreen(), group);
        }
      }
    });
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  protected Object clone() {
    return new IpnbMarkdownPanel((IpnbMarkdownCell)myCell.clone(), myParent);
  }
}
