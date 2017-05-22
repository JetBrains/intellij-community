package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbMoveCellUpAction extends AnAction {
  private final IpnbFileEditor myEditor;

  public IpnbMoveCellUpAction(IpnbFileEditor editor) {
    super("Move Cell Up");
    myEditor = editor;
  }

  public IpnbMoveCellUpAction() {
    super("Move cell up", "Move cell up", AllIcons.Actions.MoveUp);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel ipnbFilePanel = myEditor.getIpnbFilePanel();
    CommandProcessor.getInstance().executeCommand(ipnbFilePanel.getProject(),
                                                  () -> ApplicationManager.getApplication().runWriteAction(
                                                    () -> ipnbFilePanel.moveCell(false)), "Ipnb.moveCell", new Object());
  }
}
