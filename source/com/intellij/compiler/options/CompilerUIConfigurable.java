package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.RmicSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.Options;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.regex.PatternSyntaxException;

public class CompilerUIConfigurable implements Configurable {
  private JPanel myPanel;
  private JPanel myExcludeTablePanel;
  private JavaCompilersTab myJavaCompilersTab;
  private Project myProject;
  private ExcludeFromCompilePanel myExcludeFromCompilePanel;

  private JTextField myResourcePatternsField;
  private JCheckBox myCbCompileInBackground;
  private JCheckBox myCbClearOutputDirectory;
  private JPanel myTabbedPanePanel;
  private RmicConfigurable myRmicConfigurable;
  private JCheckBox myCbCloseMessageViewOnSuccess;
  private JCheckBox myCbCompileDependent;
  private JRadioButton myDoNotDeploy;
  private JRadioButton myDeploy;
  private JRadioButton myShowDialog;

  public CompilerUIConfigurable(final Project project) {
    myProject = project;

    myExcludeFromCompilePanel = new ExcludeFromCompilePanel(project);
    myExcludeFromCompilePanel.setBorder(BorderFactory.createCompoundBorder(
      IdeBorderFactory.createTitledBorder("Exclude from Compile"), BorderFactory.createEmptyBorder(2, 2, 2, 2))
    );
    myExcludeTablePanel.setLayout(new BorderLayout());
    myExcludeTablePanel.add(myExcludeFromCompilePanel, BorderLayout.CENTER);

    myTabbedPanePanel.setLayout(new BorderLayout());
    final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();
    myJavaCompilersTab = new JavaCompilersTab(project);
    tabbedPane.addTab("Java Compiler", myJavaCompilersTab.createComponent());
    myRmicConfigurable = new RmicConfigurable(RmicSettings.getInstance(project));
    tabbedPane.addTab("RMI Compiler", myRmicConfigurable.createComponent());
    myTabbedPanePanel.add(tabbedPane.getComponent(), BorderLayout.CENTER);

    myCbCompileInBackground.setMnemonic('o');
    myCbClearOutputDirectory.setMnemonic('l');
    myCbCloseMessageViewOnSuccess.setMnemonic('m');
    myCbCompileDependent.setMnemonic('d');

    ButtonGroup deployGroup = new ButtonGroup();
    deployGroup.add(myShowDialog);
    deployGroup.add(myDeploy);
    deployGroup.add(myDoNotDeploy);

  }


  public void reset() {
    myExcludeFromCompilePanel.reset();

    myJavaCompilersTab.reset();

    myRmicConfigurable.reset();

    final CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    myCbCompileInBackground.setSelected(workspaceConfiguration.COMPILE_IN_BACKGROUND);
    myCbCloseMessageViewOnSuccess.setSelected(workspaceConfiguration.CLOSE_MESSAGE_VIEW_IF_SUCCESS);
    myCbCompileDependent.setSelected(workspaceConfiguration.COMPILE_DEPENDENT_FILES);
    myCbClearOutputDirectory.setSelected(configuration.isClearOutputDirectory());

    configuration.convertPatterns();

    myResourcePatternsField.setText(patternsToString(configuration.getResourceFilePatterns()));

    if (configuration.DEPLOY_AFTER_MAKE == Options.SHOW_DIALOG) {
      myShowDialog.setSelected(true);
    }
    else if (configuration.DEPLOY_AFTER_MAKE == Options.PERFORM_ACTION_AUTOMATICALLY) {
      myDeploy.setSelected(true);
    }
    else {
      myDoNotDeploy.setSelected(true);
    }
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuffer extensionsString = new StringBuffer();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }

  public void apply() throws ConfigurationException {
    myExcludeFromCompilePanel.apply();

    myJavaCompilersTab.apply();

    myRmicConfigurable.apply();

    CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    workspaceConfiguration.COMPILE_IN_BACKGROUND = myCbCompileInBackground.isSelected();
    workspaceConfiguration.CLOSE_MESSAGE_VIEW_IF_SUCCESS = myCbCloseMessageViewOnSuccess.isSelected();
    workspaceConfiguration.COMPILE_DEPENDENT_FILES = myCbCompileDependent.isSelected();
    configuration.setClearOutputDirectory(myCbClearOutputDirectory.isSelected());

    configuration.removeResourceFilePatterns();
    String extensionString = myResourcePatternsField.getText().trim();
    applyResourcePatterns(extensionString, CompilerConfiguration.getInstance(myProject));

    configuration.DEPLOY_AFTER_MAKE = getSelectedDeploymentOption();

  }

  private static void applyResourcePatterns(String extensionString, final CompilerConfiguration configuration)
    throws ConfigurationException {
    StringTokenizer tokenizer = new StringTokenizer(extensionString, ";", false);
    java.util.List errors = new ArrayList();

    while (tokenizer.hasMoreTokens()) {
      String namePattern = tokenizer.nextToken();
      try {
        configuration.addResourceFilePattern(namePattern);
      }
      catch (PatternSyntaxException e) {
        errors.add(new String[]{namePattern, e.getMessage()});
      }
    }

    if (errors.size() > 0) {
      StringBuffer message = new StringBuffer("The following resource patterns are malformed:");
      for (Iterator it = errors.iterator(); it.hasNext();) {
        String[] pair = (String[])it.next();
        message.append("\n\n");
        message.append(pair[0]);
        message.append(": ");
        message.append(pair[1]);
      }

      throw new ConfigurationException(message.toString(), "Malformed Resource Patterns");
    }
  }

  public boolean isModified() {
    if (myExcludeFromCompilePanel.isModified()) {
      return true;
    }

    boolean isModified = false;
    isModified |= myJavaCompilersTab.isModified();
    isModified |= myRmicConfigurable.isModified();

    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    isModified |= ComparingUtils.isModified(myCbCompileInBackground, workspaceConfiguration.COMPILE_IN_BACKGROUND);
    isModified |= ComparingUtils.isModified(myCbCloseMessageViewOnSuccess, workspaceConfiguration.CLOSE_MESSAGE_VIEW_IF_SUCCESS);
    isModified |= ComparingUtils.isModified(myCbCompileDependent, workspaceConfiguration.COMPILE_DEPENDENT_FILES);

    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
    isModified |= ComparingUtils.isModified(myCbClearOutputDirectory, compilerConfiguration.isClearOutputDirectory());
    isModified |= ComparingUtils.isModified(myResourcePatternsField, patternsToString(compilerConfiguration.getResourceFilePatterns()));
    isModified |= compilerConfiguration.DEPLOY_AFTER_MAKE != getSelectedDeploymentOption();

    return isModified;
  }

  private int getSelectedDeploymentOption() {
    if (myShowDialog.isSelected()) return Options.SHOW_DIALOG;
    if (myDeploy.isSelected()) return Options.PERFORM_ACTION_AUTOMATICALLY;
    return Options.DO_NOTHING;
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public void disposeUIResources() {
  }

}