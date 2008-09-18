package com.intellij.xdebugger.impl.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable extends TabbedConfigurable implements SearchableConfigurable {
  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableDebugger.png");
  }

  public String getDisplayName() {
    return XDebuggerBundle.message("debugger.configurable.display.name");
  }

  public String getHelpTopic() {
    return "project.propDebugger";
  }

  protected List<Configurable> createConfigurables() {
    ArrayList<Configurable> configurables = new ArrayList<Configurable>();
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if(project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    DebuggerSupport[] supports = DebuggerSupport.getDebuggerSupports();
    List<DebuggerSettingsPanelProvider> providers = new ArrayList<DebuggerSettingsPanelProvider>();
    for (DebuggerSupport support : supports) {
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
    return configurables;
  }

  public boolean hasAnyConfigurables() {
    DebuggerSupport[] supports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : supports) {
      if (support.getSettingsPanelProvider().hasAnySettingsPanels()) {
        return true;
      }
    }
    return false;
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

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}