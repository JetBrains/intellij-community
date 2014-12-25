package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbCopyCellAction extends AnAction {
  public IpnbCopyCellAction() {
    super(AllIcons.Actions.Copy);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof IpnbFileEditor) {
      final IpnbFilePanel component = ((IpnbFileEditor)editor).getIpnbFilePanel();
      copyCell(component);
    }
  }

  public static void copyCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    ipnbFilePanel.copyCell();
  }
}
