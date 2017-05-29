package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class IpnbRunCellAction extends IpnbRunCellBaseAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbRunCellAction(IpnbFileEditor fileEditor) {
    super();
    getTemplatePresentation().setText("Run Cell");
    myFileEditor = fileEditor;
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
    registerCustomShortcutSet(new CustomShortcutSet(keyStroke), myFileEditor.getIpnbFilePanel());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    component.executeSaveFileCommand();
    runCell(component, true);
  }
}
