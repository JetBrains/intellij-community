package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public abstract class IpnbRunCellBaseAction extends AnAction {
  public IpnbRunCellBaseAction() {
    super(AllIcons.General.Run);
  }

  public static void runCell(@NotNull final IpnbFilePanel ipnbFilePanel, boolean selectNext) {
    final IpnbEditablePanel cell = ipnbFilePanel.getSelectedCellPanel();
    if (cell == null) return;
    cell.runCell(selectNext);
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor editor = IpnbFileEditor.DATA_KEY.getData(context);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(editor != null);
  }
}
