package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbCutCellAction extends AnAction {
  private final IpnbFileEditor myFileEditor;

  public IpnbCutCellAction(IpnbFileEditor fileEditor) {
    super("Cut Cell", "Cut Cell", AllIcons.Actions.Menu_cut);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    cutCell(component);
  }

  public static void cutCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    CommandProcessor.getInstance().executeCommand(ipnbFilePanel.getProject(), () -> ApplicationManager.getApplication().runWriteAction(
      () -> {
        ipnbFilePanel.cutCell();
        ipnbFilePanel.saveToFile(false);
      }), "Ipnb.cutCell", new Object());
  }
}
