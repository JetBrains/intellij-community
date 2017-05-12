package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class IpnbRunCellInplaceAction extends IpnbRunCellBaseAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbRunCellInplaceAction(IpnbFileEditor fileEditor) {
    super();
    getTemplatePresentation().setText("Run Cell");
    myFileEditor = fileEditor;
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    registerCustomShortcutSet(new CustomShortcutSet(keyStroke), myFileEditor.getIpnbFilePanel());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    runCell(component, false);
  }
}
