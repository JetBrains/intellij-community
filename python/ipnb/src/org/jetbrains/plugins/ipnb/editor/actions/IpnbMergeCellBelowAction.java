package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbMergeCellBelowAction extends AnAction {

  public IpnbMergeCellBelowAction() {
    super("Merge Cell Below");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    if (ipnbEditor != null) {
      final IpnbFilePanel ipnbFilePanel = ipnbEditor.getIpnbFilePanel();
      mergeCell(ipnbFilePanel);
    }
  }

  public static void mergeCell(@NotNull IpnbFilePanel filePanel) {
    filePanel.executeUndoableCommand(() -> filePanel.mergeCell(true), "Merge Cell Below");
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    if (ipnbEditor != null) {
      final IpnbFilePanel ipnbFilePanel = ipnbEditor.getIpnbFilePanel();
      final IpnbEditablePanel cell = ipnbFilePanel.getSelectedCellPanel();
      final boolean isEnabled = cell != null && ipnbFilePanel.hasNextCell(cell);
      e.getPresentation().setEnabled(isEnabled);
    }
  }
}
