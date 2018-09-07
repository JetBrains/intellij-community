package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;

public class IpnbHideOutputAction extends AnAction {
  private final IpnbCodePanel myParent;

  public IpnbHideOutputAction(@NotNull IpnbCodePanel parent) {
    super("Toggle Output Button (Double-Click)");
    myParent = parent;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myParent.hideOutputPanel();
  }
}
