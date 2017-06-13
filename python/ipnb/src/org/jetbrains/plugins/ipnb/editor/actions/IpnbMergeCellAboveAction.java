package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbMergeCellAboveAction extends AnAction {

  public IpnbMergeCellAboveAction() {
    super("Merge Cell Above");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    if (ipnbEditor != null) {
      final IpnbFilePanel ipnbFilePanel = ipnbEditor.getIpnbFilePanel();
      ipnbFilePanel.executeUndoableCommand(() -> ipnbFilePanel.mergeCell(false), "Merge Cell Above");
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    if (ipnbEditor != null) {
      final IpnbFilePanel ipnbFilePanel = ipnbEditor.getIpnbFilePanel();
      final IpnbEditablePanel cell = ipnbFilePanel.getSelectedCellPanel();
      final boolean isEnabled = cell != null && ipnbFilePanel.hasPrevCell(cell);
      e.getPresentation().setEnabled(isEnabled);
    }
  }
}
