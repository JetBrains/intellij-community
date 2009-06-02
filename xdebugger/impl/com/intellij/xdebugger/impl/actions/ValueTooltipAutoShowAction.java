package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.registry.Registry;

public class ValueTooltipAutoShowAction extends ToggleAction {
  public boolean isSelected(AnActionEvent e) {
    return Registry.is("debugger.valueTooltipAutoShow");
  }

  public void setSelected(AnActionEvent e, boolean state) {
    Registry.get("debugger.valueTooltipAutoShow").setValue(state);
  }
}