package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.configuration.IpnbConnectionManager;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class IpnbInterruptKernelAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbInterruptKernelAction(IpnbFileEditor fileEditor) {
    super("Interrupt kernel", "Interrupt kernel", AllIcons.Actions.Suspend);
    myFileEditor = fileEditor;
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_I, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    registerCustomShortcutSet(new CustomShortcutSet(keyStroke), myFileEditor.getIpnbFilePanel());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    interruptKernel(myFileEditor);
  }

  private static void interruptKernel(@NotNull final IpnbFileEditor editor) {
    final Project project = editor.getIpnbFilePanel().getProject();
    IpnbConnectionManager.getInstance(project).interruptKernel(editor.getVirtualFile().getPath());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = myFileEditor.getIpnbFilePanel().getProject();
    boolean hasConnection = IpnbConnectionManager.getInstance(project).hasConnection(myFileEditor.getVirtualFile().getPath());
    e.getPresentation().setEnabled(hasConnection);
  }
}
