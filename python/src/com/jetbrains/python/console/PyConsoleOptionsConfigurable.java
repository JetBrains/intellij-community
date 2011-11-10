package com.jetbrains.python.console;

import com.intellij.coverage.CoverageOptions;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTabbedPane;
import com.jetbrains.django.facet.DjangoFacet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class PyConsoleOptionsConfigurable implements SearchableConfigurable {
  private PyConsoleOptionsPanel myPanel;
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
    return "reference.project.settings.coverage";
  }

  @Override
  public JComponent createComponent() {
    myPanel = new PyConsoleOptionsPanel();

    return myPanel.createPanel(myProject, myOptionsProvider);
  }

  private CoverageOptions[] getExtensions() {
    return Extensions.getExtensions(CoverageOptions.EP_NAME, myProject);
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

    for (CoverageOptions coverageOptions : getExtensions()) {
      coverageOptions.disposeUIResources();
    }
  }

  private static class PyConsoleOptionsPanel {
    private JPanel myWholePanel;
    private JPanel mySpecificOptionsPanel;
    private JBCheckBox myShowDebugConsoleByDefaultCheckBox;
    private PyConsoleSpecificOptionsPanel myPythonConsoleOptionsPanel;
    private PyConsoleSpecificOptionsPanel myDjangoConsoleOptionsPanel;
    private PyConsoleOptionsProvider myOptionsProvider;

    public JPanel createPanel(Project project, PyConsoleOptionsProvider optionsProvider) {
      JBTabbedPane tabbedPane = new JBTabbedPane();

      myOptionsProvider = optionsProvider;
      mySpecificOptionsPanel.setLayout(new BorderLayout());
      mySpecificOptionsPanel.add(tabbedPane, BorderLayout.CENTER);

      myPythonConsoleOptionsPanel = new PyConsoleSpecificOptionsPanel();
      tabbedPane.addTab("Python console", myPythonConsoleOptionsPanel.createPanel(project, optionsProvider.getPythonConsoleSettings()));
      if (DjangoFacet.isPresentInAnyModule(project)) {
        myDjangoConsoleOptionsPanel = new PyConsoleSpecificOptionsPanel();
        tabbedPane.addTab("Django console", myDjangoConsoleOptionsPanel.createPanel(project, optionsProvider.getDjangoConsoleSettings()));
      }

      return myWholePanel;
    }

    public void apply() {
      myPythonConsoleOptionsPanel.apply();
      if (myDjangoConsoleOptionsPanel != null) {
        myDjangoConsoleOptionsPanel.apply();
      }
      myOptionsProvider.setShowDebugConsoleByDefault(myShowDebugConsoleByDefaultCheckBox.isSelected());
    }

    public void reset() {
      myShowDebugConsoleByDefaultCheckBox.setSelected(myOptionsProvider.isShowDebugConsoleByDefault());
      myPythonConsoleOptionsPanel.reset();
      if (myDjangoConsoleOptionsPanel != null) {
        myDjangoConsoleOptionsPanel.reset();
      }
    }

    public boolean isModified() {
      return myShowDebugConsoleByDefaultCheckBox.isSelected() != myOptionsProvider.isShowDebugConsoleByDefault() ||
             myPythonConsoleOptionsPanel.isModified() || (myDjangoConsoleOptionsPanel != null && myDjangoConsoleOptionsPanel.isModified());
    }
  }
}
