package com.intellij.xdebugger.impl.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable extends SearchableConfigurable.Parent.Abstract {
  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableDebugger.png");
  }

  public String getDisplayName() {
    return XDebuggerBundle.message("debugger.configurable.display.name");
  }

  public String getHelpTopic() {
    return "project.propDebugger";
  }

  protected Configurable[] buildConfigurables() {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if(project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    final ArrayList<Configurable> configurables = new ArrayList<Configurable>();
    final List<DebuggerSettingsPanelProvider> providers = new ArrayList<DebuggerSettingsPanelProvider>();
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      providers.add(support.getSettingsPanelProvider());
    }
    Collections.sort(providers, new Comparator<DebuggerSettingsPanelProvider>() {
      public int compare(final DebuggerSettingsPanelProvider o1, final DebuggerSettingsPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });
    for (DebuggerSettingsPanelProvider provider : providers) {
      configurables.addAll(provider.getConfigurables(project));
    }
    return configurables.toArray(new Configurable[configurables.size()]);
  }

  public void apply() throws ConfigurationException {
    super.apply();
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      support.getSettingsPanelProvider().apply();
    }
  }

  @NonNls
  public String getId() {
    return getHelpTopic();
  }
}