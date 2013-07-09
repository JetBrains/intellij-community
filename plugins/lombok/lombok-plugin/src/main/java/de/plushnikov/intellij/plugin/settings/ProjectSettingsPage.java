package de.plushnikov.intellij.plugin.settings;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 *
 */
public class ProjectSettingsPage implements SearchableConfigurable, Configurable.NoScroll {
  public static final String JAVAC_COMPILER_ID = "Javac";

  private JPanel myPanel;
  private JCheckBox myEnableLombokInProject;
  private JLabel myAnnotationConfigurationInfo1Label;
  private JLabel myAnnotationConfigurationInfo2Label;
  private JLabel myAnnotationConfigurationInfo3Label;
  private JLabel myAnnotationConfigurationOkLabel;
  private JButton checkButton;

  private Project myProject;

  public ProjectSettingsPage(Project project) {
    myProject = project;

    checkButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateAnnotationConfigurationInfo();
      }
    });
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Lombok plugin";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myEnableLombokInProject.setSelected(ProjectSettings.isEnabledInProject(myProject));
    updateAnnotationConfigurationInfo();

    return myPanel;
  }

  private void updateAnnotationConfigurationInfo() {
    boolean annotationProcessingPossible = isLombokAnnotationProcessingPossible();

    myAnnotationConfigurationOkLabel.setVisible(annotationProcessingPossible);
    myAnnotationConfigurationInfo1Label.setVisible(!annotationProcessingPossible );
    myAnnotationConfigurationInfo2Label.setVisible(!annotationProcessingPossible );
    myAnnotationConfigurationInfo3Label.setVisible(!annotationProcessingPossible );
  }

  private boolean isLombokAnnotationProcessingPossible() {
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
    boolean javacCompiler = JAVAC_COMPILER_ID.equals(((CompilerConfigurationImpl) compilerConfiguration).getDefaultCompiler().getId());
    boolean annotationProcessorsEnabled = compilerConfiguration.isAnnotationProcessorsEnabled();
    boolean externBuild = CompilerWorkspaceConfiguration.getInstance(myProject).useOutOfProcessBuild();

    return (externBuild && annotationProcessorsEnabled) || (!externBuild && !annotationProcessorsEnabled && javacCompiler);
  }

  @Override
  public boolean isModified() {
    return myEnableLombokInProject.isSelected() != ProjectSettings.isEnabledInProject(myProject);
  }

  @Override
  public void apply() throws ConfigurationException {
    ProjectSettings.setEnabledInProject(myProject, myEnableLombokInProject.isSelected());
  }

  @Override
  public void reset() {
    myEnableLombokInProject.setSelected(ProjectSettings.isEnabledInProject(myProject));
  }

  @Override
  public void disposeUIResources() {

  }

  @NotNull
  @Override
  public String getId() {
    return getDisplayName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

}
