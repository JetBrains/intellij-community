package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbMoveCellDownAction extends AnAction {
  private final IpnbFileEditor myEditor;

  public IpnbMoveCellDownAction(IpnbFileEditor editor) {
    super("Move Cell Down");
    myEditor = editor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel ipnbFilePanel = myEditor.getIpnbFilePanel();
    CommandProcessor.getInstance().executeCommand(ipnbFilePanel.getProject(), () -> ApplicationManager.getApplication().runWriteAction(
      () -> ipnbFilePanel.moveCell(true)), "Ipnb.moveCell", new Object());
  }
}
