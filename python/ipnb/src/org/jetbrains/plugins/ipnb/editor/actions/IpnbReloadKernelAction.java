package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;

public class IpnbReloadKernelAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbReloadKernelAction(IpnbFileEditor fileEditor) {
    super("Restart Kernel", "Restart Kernel", AllIcons.Actions.Refresh);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    reloadKernel(myFileEditor);
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

  @Override
  public void update(AnActionEvent e) {
    final Project project = myFileEditor.getIpnbFilePanel().getProject();
    boolean hasConnection = IpnbConnectionManager.getInstance(project).hasConnection(myFileEditor.getVirtualFile().getPath());
    e.getPresentation().setEnabled(hasConnection);
  }
}
