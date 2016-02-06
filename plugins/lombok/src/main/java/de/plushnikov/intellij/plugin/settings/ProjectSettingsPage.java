package de.plushnikov.intellij.plugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import de.plushnikov.intellij.plugin.provider.LombokProcessorProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProjectSettingsPage implements SearchableConfigurable, Configurable.NoScroll {

  private JPanel myGeneralPanel;
  private JPanel myLombokPanel;
  private JPanel myThirdPartyPanel;

  private JCheckBox myEnableLombokInProject;

  private JCheckBox myEnableValSupport;
  private JCheckBox myEnableBuilderSupport;
  private JCheckBox myEnableLogSupport;
  private JCheckBox myEnableConstructorSupport;
  private JCheckBox myEnableDelegateSupport;
  private JCheckBox myEnableParcelableSupport;

  private Project myProject;

  public ProjectSettingsPage(Project project) {
    myProject = project;
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
    initFromSettings();

    // Add Listener to deactivate all checkboxes if plugin is deactivated
    myEnableLombokInProject.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
        boolean selected = checkBox.getModel().isSelected();

        myLombokPanel.setEnabled(selected);
        myThirdPartyPanel.setEnabled(selected);

        myEnableValSupport.setEnabled(selected);
        myEnableBuilderSupport.setEnabled(selected);
        myEnableLogSupport.setEnabled(selected);
        myEnableConstructorSupport.setEnabled(selected);
        myEnableDelegateSupport.setEnabled(selected);
        myEnableParcelableSupport.setEnabled(selected);
      }
    });
    myEnableConstructorSupport.setVisible(false);
    return myGeneralPanel;
  }

  private void initFromSettings() {

    myEnableLombokInProject.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.LOMBOK_ENABLED_IN_PROJECT));
    myEnableValSupport.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.IS_VAL_ENABLED));
    myEnableBuilderSupport.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.IS_BUILDER_ENABLED));
    myEnableDelegateSupport.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.IS_DELEGATE_ENABLED));

    myEnableLogSupport.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.IS_LOG_ENABLED));
    myEnableConstructorSupport.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.IS_CONSTRUCTOR_ENABLED));

    myEnableParcelableSupport.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.IS_THIRD_PARTY_ENABLED));
  }

  @Override
  public boolean isModified() {
    return myEnableLombokInProject.isSelected() != ProjectSettings.isEnabled(myProject, ProjectSettings.LOMBOK_ENABLED_IN_PROJECT) ||
        myEnableValSupport.isSelected() != ProjectSettings.isEnabled(myProject, ProjectSettings.IS_VAL_ENABLED) ||
        myEnableBuilderSupport.isSelected() != ProjectSettings.isEnabled(myProject, ProjectSettings.IS_BUILDER_ENABLED) ||
        myEnableDelegateSupport.isSelected() != ProjectSettings.isEnabled(myProject, ProjectSettings.IS_DELEGATE_ENABLED) ||
        myEnableLogSupport.isSelected() != ProjectSettings.isEnabled(myProject, ProjectSettings.IS_LOG_ENABLED) ||
        myEnableConstructorSupport.isSelected() != ProjectSettings.isEnabled(myProject, ProjectSettings.IS_CONSTRUCTOR_ENABLED) ||
        myEnableParcelableSupport.isSelected() != ProjectSettings.isEnabled(myProject, ProjectSettings.IS_THIRD_PARTY_ENABLED);
  }

  @Override
  public void apply() throws ConfigurationException {
    ProjectSettings.setEnabled(myProject, ProjectSettings.LOMBOK_ENABLED_IN_PROJECT, myEnableLombokInProject.isSelected());

    ProjectSettings.setEnabled(myProject, ProjectSettings.IS_VAL_ENABLED, myEnableValSupport.isSelected());
    ProjectSettings.setEnabled(myProject, ProjectSettings.IS_BUILDER_ENABLED, myEnableBuilderSupport.isSelected());
    ProjectSettings.setEnabled(myProject, ProjectSettings.IS_DELEGATE_ENABLED, myEnableDelegateSupport.isSelected());

    ProjectSettings.setEnabled(myProject, ProjectSettings.IS_LOG_ENABLED, myEnableLogSupport.isSelected());
    ProjectSettings.setEnabled(myProject, ProjectSettings.IS_CONSTRUCTOR_ENABLED, myEnableConstructorSupport.isSelected());

    ProjectSettings.setEnabled(myProject, ProjectSettings.IS_THIRD_PARTY_ENABLED, myEnableParcelableSupport.isSelected());

    LombokProcessorProvider.getInstance().initProcessors(myProject);
  }

  @Override
  public void reset() {
    initFromSettings();
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
