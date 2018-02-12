package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

import java.util.Collections;
import java.util.List;

public class IpnbRunAllCellsAction extends IpnbRunCellBaseAction {
  public IpnbRunAllCellsAction() {
    super();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    if (ipnbEditor != null) {
      final IpnbFilePanel ipnbFilePanel = ipnbEditor.getIpnbFilePanel();
      final List<IpnbEditablePanel> cells = ipnbFilePanel.getIpnbPanels();
      if (cells.isEmpty()) return;
      runCells(Collections.unmodifiableList(cells), ipnbFilePanel);
    }
  }

  private static void runCells(List<IpnbEditablePanel> cells, IpnbFilePanel ipnbFilePanel) {
    for (int i = 0; i < cells.size(); i++) {
      IpnbEditablePanel cell = cells.get(i);
      final int nextIndex = i + 1;
      if (nextIndex < cells.size()) {
        final IpnbEditablePanel nextCell = cells.get(nextIndex);
        cell.onFinishExecutionAction(() -> nextCell.runCell(nextIndex != cells.size() - 1));
      }
    }
    final IpnbEditablePanel firstPanel = cells.get(0);
    ipnbFilePanel.setSelectedCell(firstPanel, false);
    firstPanel.runCell(true);

    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
                                                                 IdeFocusManager.getGlobalInstance().requestFocus(ipnbFilePanel, true));
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(ipnbEditor != null);
  }
}
