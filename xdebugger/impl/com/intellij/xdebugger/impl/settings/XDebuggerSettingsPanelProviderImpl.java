package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.xdebugger.settings.XDebuggerSettings;

import java.util.Collection;
import java.util.ArrayList;

/**
 * @author nik
 */
public class XDebuggerSettingsPanelProviderImpl extends DebuggerSettingsPanelProvider {
  public int getPriority() {
    return 0;
  }

  public Collection<? extends Configurable> getConfigurables(final Project project) {
    ArrayList<Configurable> list = new ArrayList<Configurable>();
    XDebuggerSettings[] settingses = Extensions.getExtensions(XDebuggerSettings.EXTENSION_POINT);
    for (XDebuggerSettings settings : settingses) {
      list.add(settings.createConfigurable());
    }
    return list;
  }

  @Override
  public boolean hasAnySettingsPanels() {
    return Extensions.getExtensions(XDebuggerSettings.EXTENSION_POINT).length > 0;
  }
}
