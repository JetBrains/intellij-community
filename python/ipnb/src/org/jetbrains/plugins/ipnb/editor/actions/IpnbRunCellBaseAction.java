package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public abstract class IpnbRunCellBaseAction extends AnAction {

  public IpnbRunCellBaseAction() {
    super("Run Cell", "Run Cell", AllIcons.Actions.Execute);
  }

  public static void runCell(@NotNull final IpnbFilePanel ipnbFilePanel, boolean selectNext) {
    final IpnbEditablePanel cell = ipnbFilePanel.getSelectedCellPanel();
    if (cell == null) return;
    cell.onFinishExecutionAction(null);
    cell.runCell(selectNext);
  }
}
