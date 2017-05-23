package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbPasteCellAction extends AnAction {
  private final IpnbFileEditor myFileEditor;

  public IpnbPasteCellAction(@NotNull IpnbFileEditor fileEditor) {
    super("Paste Cell Below", "Paste Cell Below", AllIcons.Actions.Menu_paste);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    pasteCell(component);
  }

  public static void pasteCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    CommandProcessor.getInstance().executeCommand(ipnbFilePanel.getProject(), () -> ApplicationManager.getApplication().runWriteAction(
      () -> {
        ipnbFilePanel.pasteCell();
        ipnbFilePanel.saveToFile(false);
      }), "Ipnb.pasteCell", new Object());

  }
}
