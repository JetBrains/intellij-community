package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;

public class IpnbReloadKernelAction extends AnAction {
  public IpnbReloadKernelAction() {
    super();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof IpnbFileEditor) {
      reloadKernel(((IpnbFileEditor)editor));
    }
  }
  public static void reloadKernel(@NotNull final IpnbFileEditor editor) {
    final Project project = editor.getIpnbFilePanel().getProject();
    @SuppressWarnings("DialogTitleCapitalization")
    final int restart = Messages.showYesNoDialog("Do you want to restart the current kernel? You will lose all variables defined in it.",
                                                 "Restart kernel or continue running?", "Restart", "Continue running", null);
    if (restart == Messages.OK) {
      IpnbConnectionManager.getInstance(project).reloadKernel(editor.getVirtualFile().getPath());
    }
  }
}
