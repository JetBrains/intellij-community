package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;

public class IpnbInterruptKernelAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbInterruptKernelAction(IpnbFileEditor fileEditor) {
    super("Interrupt kernel", "Interrupt kernel", AllIcons.Actions.Suspend);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    interruptKernel(myFileEditor);
  }

  public static void interruptKernel(@NotNull final IpnbFileEditor editor) {
    final Project project = editor.getIpnbFilePanel().getProject();
    IpnbConnectionManager.getInstance(project).interruptKernel(editor.getVirtualFile().getPath());
  }
}
