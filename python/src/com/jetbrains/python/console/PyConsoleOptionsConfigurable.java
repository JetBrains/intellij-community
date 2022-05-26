// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class PyConsoleOptionsConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll {
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE = "reference.project.settings.console";
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON = "reference.project.settings.console.python";

  private PyConsoleOptionsPanel myPanel;

  private final Project myProject;

  public PyConsoleOptionsConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull String getId() {
    return "pyconsole";
  }

  @Override
  protected Configurable[] buildConfigurables() {
    List<Configurable> result = new ArrayList<>();

    PyConsoleSpecificOptionsPanel pythonConsoleOptionsPanel = new PyConsoleSpecificOptionsPanel(myProject);
    result.add(createConsoleChildConfigurable(PyBundle.message("configurable.PyConsoleOptionsConfigurable.child.display.name"), pythonConsoleOptionsPanel,
                                              PyConsoleOptions.getInstance(myProject).getPythonConsoleSettings(), CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON));

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

  private static Configurable createConsoleChildConfigurable(final @NlsContexts.ConfigurableName String name,
                                                             final PyConsoleSpecificOptionsPanel panel,
                                                             final PyConsoleOptions.PyConsoleSettings settings, final String helpReference) {
    return new SearchableConfigurable() {

      @Override
      public @NotNull String getId() {
        return "PyConsoleConfigurable." + name;
      }

      @Override
      public @Nls String getDisplayName() {
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
      public void apply() {
        panel.apply();
      }

      @Override
      public void reset() {
        panel.reset();
      }
    };
  }

  @Override
  public @Nls String getDisplayName() {
    return PyBundle.message("configurable.PyConsoleOptionsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return CONSOLE_SETTINGS_HELP_REFERENCE;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new PyConsoleOptionsPanel();

    return myPanel.createPanel(PyConsoleOptions.getInstance(myProject));
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() {
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
