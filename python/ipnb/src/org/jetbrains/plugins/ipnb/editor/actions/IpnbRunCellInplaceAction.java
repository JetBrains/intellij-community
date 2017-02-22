package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbRunCellInplaceAction extends IpnbRunCellBaseAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbRunCellInplaceAction(IpnbFileEditor fileEditor) {
    super();
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    runCell(component, false);
  }
}
