package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;

public class IpnbToggleLineNumbersAction extends AnAction {
  private final IpnbCodePanel myPanel;

  public IpnbToggleLineNumbersAction(IpnbCodePanel panel) {
    super("Toggle line numbers");
    myPanel = panel;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    toggleLineNumbers(myPanel);
  }

  public static void toggleLineNumbers(IpnbCodePanel panel) {
    final Editor editor = panel.getEditor();
    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setLineNumbersShown(!editorSettings.isLineNumbersShown());
  }
}
