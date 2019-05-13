// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.google.common.collect.Lists;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author traff
 */
public class PyConsoleOptionsConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll{
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE = "reference.project.settings.console";
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON = "reference.project.settings.console.python";

  private PyConsoleOptionsPanel myPanel;

  private final PyConsoleOptions myOptionsProvider;
  private final Project myProject;

  public PyConsoleOptionsConfigurable(PyConsoleOptions optionsProvider, Project project) {
    myOptionsProvider = optionsProvider;
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return "pyconsole";
  }

  @Override
  protected Configurable[] buildConfigurables() {
    List<Configurable> result = Lists.newArrayList();

    PyConsoleSpecificOptionsPanel pythonConsoleOptionsPanel = new PyConsoleSpecificOptionsPanel(myProject);
    result.add(createConsoleChildConfigurable("Python Console", pythonConsoleOptionsPanel,
                                              myOptionsProvider.getPythonConsoleSettings(), CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON));

    for (PyConsoleOptionsProvider provider : PyConsoleOptionsProvider.EP_NAME.getExtensionList()) {
      if (provider.isApplicableTo(myProject)) {
        result.add(createConsoleChildConfigurable(provider.getName(),
                                                  new PyConsoleSpecificOptionsPanel(myProject),
                                                  provider.getSettings(myProject),
                                                  provider.getHelpTopic()));
      }
    }

    return result.toArray(new Configurable[0]);
  }

  private static Configurable createConsoleChildConfigurable(final String name,
                                                             final PyConsoleSpecificOptionsPanel panel,
                                                             final PyConsoleOptions.PyConsoleSettings settings, final String helpReference) {
    return new SearchableConfigurable() {

      @NotNull
      @Override
      public String getId() {
        return "PyConsoleConfigurable." + name;
      }

      @Nls
      @Override
      public String getDisplayName() {
        return name;
      }

      @Override
      public String getHelpTopic() {
        return helpReference;
      }

      @Override
      public JComponent createComponent() {
        return panel.createPanel(settings);
      }

      @Override
      public boolean isModified() {
        return panel.isModified();
      }

      @Override
      public void apply() throws ConfigurationException {
        panel.apply();
      }

      @Override
      public void reset() {
        panel.reset();
      }

      @Override
      public void disposeUIResources() {
      }
    };
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Console";
  }

  @Override
  public String getHelpTopic() {
    return CONSOLE_SETTINGS_HELP_REFERENCE;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new PyConsoleOptionsPanel();

    return myPanel.createPanel(myOptionsProvider);
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }


  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  private static class PyConsoleOptionsPanel {
    private JPanel myWholePanel;
    private JBCheckBox myShowDebugConsoleByDefault;
    private JBCheckBox myIpythonEnabledCheckbox;
    private JBCheckBox myShowsVariablesByDefault;
    private JBCheckBox myUseExistingConsole;
    private PyConsoleOptions myOptionsProvider;

    public JPanel createPanel(PyConsoleOptions optionsProvider) {
      myOptionsProvider = optionsProvider;

      return myWholePanel;
    }

    public void apply() {
      myOptionsProvider.setShowDebugConsoleByDefault(myShowDebugConsoleByDefault.isSelected());
      myOptionsProvider.setIpythonEnabled(myIpythonEnabledCheckbox.isSelected());
      myOptionsProvider.setShowVariablesByDefault(myShowsVariablesByDefault.isSelected());
      myOptionsProvider.setUseExistingConsole(myUseExistingConsole.isSelected());
    }

    public void reset() {
      myShowDebugConsoleByDefault.setSelected(myOptionsProvider.isShowDebugConsoleByDefault());
      myIpythonEnabledCheckbox.setSelected(myOptionsProvider.isIpythonEnabled());
      myShowsVariablesByDefault.setSelected(myOptionsProvider.isShowVariableByDefault());
      myUseExistingConsole.setSelected(myOptionsProvider.isUseExistingConsole());
    }

    public boolean isModified() {
      return myShowDebugConsoleByDefault.isSelected() != myOptionsProvider.isShowDebugConsoleByDefault() ||
             myIpythonEnabledCheckbox.isSelected()  != myOptionsProvider.isIpythonEnabled() ||
             myShowsVariablesByDefault.isSelected() != myOptionsProvider.isShowVariableByDefault() ||
             myUseExistingConsole.isSelected() != myOptionsProvider.isUseExistingConsole();

    }
  }
}
