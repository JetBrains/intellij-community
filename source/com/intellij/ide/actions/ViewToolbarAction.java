
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

public class ViewToolbarAction extends ToggleAction {
  public boolean isSelected(AnActionEvent event) {
    return UISettings.getInstance().SHOW_MAIN_TOOLBAR;
  }

  public void setSelected(AnActionEvent event,boolean state) {
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.SHOW_MAIN_TOOLBAR=state;
    uiSettings.fireUISettingsChanged();
  }
}
