package com.intellij.execution.impl;

import com.intellij.execution.RunManagerConfig;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

class RunConfigurable extends BaseConfigurable {
  private final Project myProject;
  private final RunDialog myRunDialog;

  private JPanel myPanel;
  private TabbedPaneWrapper myTabbedPane;
  private ConfigurationTab[] myTabs;
  private JCheckBox myCbShowSettingsBeforeRunning;
  private JCheckBox myCbCompileBeforeRunning;
  private static final Icon ICON = IconLoader.getIcon("/general/configurableRunDebug.png");

  public RunConfigurable(final Project project) {
    myProject = project;
    myRunDialog = null;
  }

  public RunConfigurable(final Project project, final RunDialog runDialog) {
    myProject = project;
    myRunDialog = runDialog;
  }

  public String getDisplayName() {
    return "Run";
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new BorderLayout());

    myTabbedPane = new TabbedPaneWrapper();

    final ConfigurationType[] factories = getConfigurationFactories();
    myTabs = new ConfigurationTab[factories.length];
    for (int i = 0; i < factories.length; i++) {
      final ConfigurationType type = factories[i];
      final ConfigurationTab configurationTab = new ConfigurationTab(type, this);
      myTabs[i] = configurationTab;
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
      panel.add(configurationTab.getComponent(), BorderLayout.CENTER);
      myTabbedPane.addTab(type.getDisplayName(), panel);
      myTabbedPane.setIconAt(i, type.getIcon());
    }

    myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    final JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 5, 0));
    myCbShowSettingsBeforeRunning = new JCheckBox("Display settings before running/debugging");
    myCbShowSettingsBeforeRunning.setMnemonic('D');
    bottomPanel.add(myCbShowSettingsBeforeRunning);

    myCbCompileBeforeRunning = new JCheckBox("Make module before running/debugging/reloading");
    myCbCompileBeforeRunning.setMnemonic('M');
    bottomPanel.add(myCbCompileBeforeRunning);

    myPanel.add(bottomPanel, BorderLayout.SOUTH);
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    myTabbedPane.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        updateDialog();
      }
    });
    final ItemListener cbListener = new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        setModified(true);
      }
    };
    myCbCompileBeforeRunning.addItemListener(cbListener);
    myCbShowSettingsBeforeRunning.addItemListener(cbListener);

    updateDialog();
    return myPanel;
  }

  public Icon getIcon() {
    return ICON;
  }

  public void reset() {
    for (int i = 0; i < myTabs.length; i++) {
      myTabs[i].reset();
    }

    final RunManagerEx manager = getRunManager();
    final RunManagerConfig config = manager.getConfig();
    myCbShowSettingsBeforeRunning.setSelected(config.isShowSettingsBeforeRun());
    myCbCompileBeforeRunning.setSelected(config.isCompileBeforeRunning());

    final ConfigurationType activeType = manager.getActiveConfigurationFactory();
    final ConfigurationType[] factories = getConfigurationFactories();
    int tabToSelect = 0;
    for (int i = 0; i < factories.length; i++) {
      if (factories[i].equals(activeType)) {
        tabToSelect = i;
        break;
      }
    }
    myTabbedPane.setSelectedIndex(tabToSelect);
    setModified(false);
  }

  public Project getProject() {
    return myProject;
  }

  public void apply() throws ConfigurationException {
    for (int i = 0; i < myTabs.length; i++) {
      try {
        myTabs[i].apply();
      }
      catch (ConfigurationException e) {
        myTabbedPane.setSelectedIndex(i);
        throw e;
      }
    }
    final RunManagerEx manager = getRunManager();
    manager.setActiveConfigurationFactory(getSelectedConfigType());

    manager.getConfig().setShowSettingsBeforeRun(myCbShowSettingsBeforeRunning.isSelected());
    manager.getConfig().setCompileBeforeRunning(myCbCompileBeforeRunning.isSelected());

    setModified(false);
  }

  public ConfigurationType getSelectedConfigType() {
    return getConfigurationFactories()[myTabbedPane.getSelectedIndex()];
  }

  public boolean isModified() {
    if (super.isModified()) return true;
    for (int i = 0; i < myTabs.length; i++) {
      if (myTabs[i].isModified()) {
        return true;
      }
    }
    return false;
  }

  public void disposeUIResources() {
    for (int i = 0; i < myTabs.length; i++) {
      final ConfigurationTab tab = myTabs[i];
      tab.disposeUIResources();
    }
    myPanel = null;
  }

  private ConfigurationType[] getConfigurationFactories() {
    return getRunManager().getConfigurationFactories();
  }

  void updateDialog() {
    if (myRunDialog == null) return;

    final StringBuffer buffer = new StringBuffer();
    buffer.append(myRunDialog.getRunnerInfo().getId());
    final ConfigurationTab tab = fromComponent(myTabbedPane.getSelectedComponent());
    if (tab != null) {
      final RunnerAndConfigurationSettingsImpl configuration = tab.getSelectedConfiguration();
      if (configuration != null) {
        buffer.append(" - ");
        buffer.append(configuration.getName());
      }
      myRunDialog.setOKActionEnabled(tab.canRunConfiguration(configuration));
    }
    myRunDialog.setTitle(buffer.toString());
  }

  public static RunConfiguration createSameConfiguration(RunConfiguration configuration, RunManagerEx runManager) {
    return runManager.createConfiguration(configuration.getName(), configuration.getFactory()).getConfiguration();
  }                   

  private RunManagerImpl getRunManager() {
    return RunManagerImpl.getInstanceImpl(myProject);
  }

  public static ConfigurationTab fromComponent(final JComponent component) {
    return (ConfigurationTab)component.getClientProperty(KEY);
  }

  static final Key<ConfigurationTab> KEY = new Key<ConfigurationTab>("configurationTab");

  public String getHelpTopic() {
    return "project.propRunDebug";
  }

  public void clickDefaultButton() {
    if (myRunDialog != null) myRunDialog.clickDefaultButton();
  }
}
