package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;

public class IpnbSaveAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbSaveAction(IpnbFileEditor fileEditor) {
    super("Save and Checkpoint", "Save and Checkpoint", AllIcons.Actions.Menu_saveall);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    saveAndCheckpoint(myFileEditor);
  }

  public static void saveAndCheckpoint(@NotNull final IpnbFileEditor editor) {
    final IpnbFilePanel filePanel = editor.getIpnbFilePanel();
    IpnbParser.saveIpnbFile(filePanel);
    final VirtualFile file = editor.getVirtualFile();
    file.refresh(false, false);
  }
}
