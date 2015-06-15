package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;

public class IpnbInterruptKernelAction extends AnAction {
  public IpnbInterruptKernelAction() {
    super();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof IpnbFileEditor) {
      interruptKernel(((IpnbFileEditor)editor));
    }
  }
  public static void interruptKernel(@NotNull final IpnbFileEditor editor) {
    final Project project = editor.getIpnbFilePanel().getProject();
    IpnbConnectionManager.getInstance(project).interruptKernel(editor.getVirtualFile().getPath());
  }
}
