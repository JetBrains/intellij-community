package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbCopyCellAction extends AnAction {
  private final IpnbFileEditor myFileEditor;

  public IpnbCopyCellAction(IpnbFileEditor fileEditor) {
    super("Copy Cell", "Copy Cell", AllIcons.Actions.Copy);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    copyCell(component);
  }

  public static void copyCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    ipnbFilePanel.copyCell();
  }
}
