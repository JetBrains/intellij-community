package com.jetbrains.python.console;

import com.google.common.collect.Lists;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.django.facet.DjangoFacet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author traff
 */
public class PyConsoleOptionsConfigurable extends SearchableConfigurable.Parent.Abstract {
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE = "reference.project.settings.console";
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON = "reference.project.settings.console.python";
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE_DJANGO = "reference.project.settings.console.django";


  private PyConsoleOptionsPanel myPanel;
  private PyConsoleSpecificOptionsPanel myPythonConsoleOptionsPanel;
  private PyConsoleSpecificOptionsPanel myDjangoConsoleOptionsPanel;

  private final PyConsoleOptionsProvider myOptionsProvider;
  private Project myProject;

  public PyConsoleOptionsConfigurable(PyConsoleOptionsProvider optionsProvider, Project project) {
    myOptionsProvider = optionsProvider;
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return "coverage";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  protected Configurable[] buildConfigurables() {
    List<Configurable> result = Lists.newArrayList();

    myPythonConsoleOptionsPanel = new PyConsoleSpecificOptionsPanel();
    result.add(createConsoleChildConfigurable("Python console", myPythonConsoleOptionsPanel,
                                              myOptionsProvider.getPythonConsoleSettings(), CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON));

    if (DjangoFacet.isPresentInAnyModule(myProject)) {
      myDjangoConsoleOptionsPanel = new PyConsoleSpecificOptionsPanel();
      result.add(createConsoleChildConfigurable("Django console",
                                                myDjangoConsoleOptionsPanel, myOptionsProvider.getDjangoConsoleSettings(),
                                                CONSOLE_SETTINGS_HELP_REFERENCE_DJANGO));
    }


    return result.toArray(new Configurable[result.size()]);
  }

  private Configurable createConsoleChildConfigurable(final String name,
                                                      final PyConsoleSpecificOptionsPanel panel,
                                                      final PyConsoleOptionsProvider.PyConsoleSettings settings, final String helpReference) {
    return new SearchableConfigurable() {

      @NotNull
      @Override
      public String getId() {
        return "PyConsoleConfigurable." + name;
      }

      @Override
      public Runnable enableSearch(String option) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
      }

      @Nls
      @Override
      public String getDisplayName() {
        return name;
      }

      @Override
      public Icon getIcon() {
        return null;
      }

      @Override
      public String getHelpTopic() {
        return helpReference;
      }

      @Override
      public JComponent createComponent() {
        return panel.createPanel(myProject, settings);
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
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return CONSOLE_SETTINGS_HELP_REFERENCE;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new PyConsoleOptionsPanel();

    return myPanel.createPanel(myProject, myOptionsProvider);
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
    private JBCheckBox myShowSeparatorLine;
    private PyConsoleOptionsProvider myOptionsProvider;

    public JPanel createPanel(Project project, PyConsoleOptionsProvider optionsProvider) {
      myOptionsProvider = optionsProvider;

      return myWholePanel;
    }

    public void apply() {
      myOptionsProvider.setShowDebugConsoleByDefault(myShowDebugConsoleByDefault.isSelected());
      myOptionsProvider.setShowSeparatorLine(myShowSeparatorLine.isSelected());
    }

    public void reset() {
      myShowDebugConsoleByDefault.setSelected(myOptionsProvider.isShowDebugConsoleByDefault());
      myShowSeparatorLine.setSelected(myOptionsProvider.isShowSeparatorLine());
    }

    public boolean isModified() {
      return myShowDebugConsoleByDefault.isSelected() != myOptionsProvider.isShowDebugConsoleByDefault() ||
             myShowSeparatorLine.isSelected() != myOptionsProvider.isShowSeparatorLine();

    }
  }
}
