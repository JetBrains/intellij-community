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
public class DebuggerConfigurable implements SearchableConfigurable.Parent {
  private Configurable myRootConfigurable;
  private Configurable[] myChildren;

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableDebugger.png");
  }

  public String getDisplayName() {
    return XDebuggerBundle.message("debugger.configurable.display.name");
  }

  public String getHelpTopic() {
    return myRootConfigurable != null? myRootConfigurable.getHelpTopic() : null;
  }

  public Configurable[] getConfigurables() {
    if (myChildren == null) {
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
        final Configurable rootConfigurable = provider.getRootConfigurable();
        if (rootConfigurable != null) {
          if (myRootConfigurable != null) {
            configurables.add(rootConfigurable);
          }
          else {
            myRootConfigurable = rootConfigurable;
          }
        }
      }
      myChildren = configurables.toArray(new Configurable[configurables.size()]);
    }
    return myChildren;
  }

  public void apply() throws ConfigurationException {
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      support.getSettingsPanelProvider().apply();
    }
    if (myRootConfigurable != null) {
      myRootConfigurable.apply();
    }
  }

  public boolean hasOwnContent() {
    return myRootConfigurable != null;
  }

  public boolean isVisible() {
    return true;
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public JComponent createComponent() {
    return myRootConfigurable != null ? myRootConfigurable.createComponent() : null;
  }

  public boolean isModified() {
    return myRootConfigurable != null && myRootConfigurable.isModified();
  }

  public void reset() {
    if (myRootConfigurable != null) {
      myRootConfigurable.reset();
    }
  }

  public void disposeUIResources() {
    myChildren = null;
    myRootConfigurable = null;
  }

  @NonNls
  public String getId() {
    return "project.propDebugger";
  }
}