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
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.IpnbParser;

public class IpnbSaveAction extends AnAction {
  public IpnbSaveAction() {
    super(AllIcons.General.Run);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof IpnbFileEditor) {
      final IpnbFilePanel component = ((IpnbFileEditor)editor).getIpnbFilePanel();
      saveAndCheckpoint(component.getIpnbFile());
    }
  }

  public void saveAndCheckpoint(@NotNull final IpnbFile ipnbFile) {
    IpnbParser.saveIpnbFile(ipnbFile);
  }
}
