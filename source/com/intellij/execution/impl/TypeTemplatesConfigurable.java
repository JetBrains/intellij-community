/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.impl;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;

/**
 * @author dyoma
 */
class TypeTemplatesConfigurable implements Configurable {
  private final RunManagerImpl myRunManager;
  private final ConfigurationType myType;
  private final Configurable[] myConfigurables;
  private TabbedPaneWrapper myTabbedPane;

  public TypeTemplatesConfigurable(final ConfigurationType type, final RunManagerImpl runManager) {
    myRunManager = runManager;
    myType = type;
    myConfigurables = new Configurable[getFactories().length];
    myTabbedPane = new TabbedPaneWrapper();
  }

  private ConfigurationFactory[] getFactories() {
    return myType.getConfigurationFactories();
  }

  public String getDisplayName() {
    return ExecutionBundle.message("template.settings.configurable.display.name");
  }

  public Icon getIcon() {
    return myType.getIcon();
  }

  public String getHelpTopic() {
    final int index = myTabbedPane.getSelectedIndex();
    return myConfigurables[index].getHelpTopic();
  }

  public JComponent createComponent() {
    final ConfigurationFactory[] factories = getFactories();
    for (int i = 0; i < factories.length; i++) {
      final ConfigurationFactory factory = factories[i];
      final RunnerAndConfigurationSettingsImpl template = myRunManager.getConfigurationTemplate(factory);
      final Configurable configurable = new TemplateConfigurable(template);
      myConfigurables[i] = configurable;
      myTabbedPane.addTab(factory.getName(), configurable.getIcon(), configurable.createComponent(), null);
    }
    return myTabbedPane.getComponent();
  }

  public boolean isModified() {
    for (int i = 0; i < myConfigurables.length; i++) {
      final Configurable configurable = myConfigurables[i];
      if (configurable.isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (int i = 0; i < myConfigurables.length; i++) {
      final Configurable configurable = myConfigurables[i];
      configurable.apply();
    }
  }

  public void reset() {
    for (int i = 0; i < myConfigurables.length; i++) {
      final Configurable configurable = myConfigurables[i];
      configurable.reset();
    }
  }

  public void disposeUIResources() {
    for (int i = 0; i < myConfigurables.length; i++) {
      final Configurable configurable = myConfigurables[i];
      configurable.disposeUIResources();
      myConfigurables[i] = null;
    }
    myTabbedPane = null;
  }

  public static Configurable createConfigurable(final ConfigurationType type, final Project project) {
    final ConfigurationFactory[] factories = type.getConfigurationFactories();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    return factories.length == 1
           ? (Configurable)new TemplateConfigurable(runManager.getConfigurationTemplate(factories[0]))
           : new TypeTemplatesConfigurable(type, runManager);
  }

  private static class TemplateConfigurable extends SettingsEditorConfigurable {
    private final RunnerAndConfigurationSettings myTemplate;

    public TemplateConfigurable(RunnerAndConfigurationSettingsImpl template) {
      super(new ConfigurationSettingsEditor(template), template);
      myTemplate = template;
    }

    public String getDisplayName() {
      return myTemplate.getConfiguration().getName();
    }

    public Icon getIcon() {
      return myTemplate.getConfiguration().getType().getIcon();
    }

    public String getHelpTopic() {
      return null;
    }
  }
}
