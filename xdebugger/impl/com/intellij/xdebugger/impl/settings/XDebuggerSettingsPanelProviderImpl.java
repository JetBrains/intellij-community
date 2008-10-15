package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.settings.XDebuggerSettings;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author nik
 */
public class XDebuggerSettingsPanelProviderImpl extends DebuggerSettingsPanelProvider {
  public int getPriority() {
    return 0;
  }

  public Collection<? extends Configurable> getConfigurables(final Project project) {
    ArrayList<Configurable> list = new ArrayList<Configurable>();
    for (XDebuggerSettings settings : XDebuggerSettingsManager.getInstance().getSettingsList()) {
      list.add(settings.createConfigurable());
    }
    return list;
  }

  @Override
  public boolean hasAnySettingsPanels() {
    return !XDebuggerSettingsManager.getInstance().getSettingsList().isEmpty();
  }
}
