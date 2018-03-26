// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportWizardStep;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.project.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MavenProjectImportStep extends ProjectImportWizardStep {
  private final JPanel myPanel;
  private final NamePathComponent myRootPathComponent;
  private final MavenImportingSettingsForm myImportingSettingsForm;

  public MavenProjectImportStep(WizardContext wizardContext) {
    super(wizardContext);

    myImportingSettingsForm = new MavenImportingSettingsForm(true, wizardContext.isCreatingNewProject()) {
      public String getDefaultModuleDir() {
        return myRootPathComponent.getPath();
      }
    };

    myRootPathComponent = new NamePathComponent("",
                                                ProjectBundle.message("maven.import.label.select.root"),
                                                ProjectBundle.message("maven.import.title.select.root"),
                                                "",
                                                false,
                                                false);

    JButton envSettingsButton = new JButton(ProjectBundle.message("maven.import.environment.settings"));
    envSettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ShowSettingsUtil.getInstance().editConfigurable(myPanel, new MavenEnvironmentConfigurable());
      }
    });

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = JBUI.insets(4, 4, 0, 4);

    myPanel.add(myRootPathComponent, c);

    c.gridy = 1;
    c.insets = JBUI.insets(4, 4, 0, 4);
    myPanel.add(myImportingSettingsForm.createComponent(), c);

    c.gridy = 2;
    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.NORTHEAST;
    c.weighty = 1;
    c.insets = JBUI.insets(4 + envSettingsButton.getPreferredSize().height, 4, 4, 4);
    myPanel.add(envSettingsButton, c);

    myRootPathComponent.setNameComponentVisible(false);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    MavenImportingSettings settings = getImportingSettings();
    myImportingSettingsForm.getData(settings);
    if (getWizardContext().isCreatingNewProject()) {
      myImportingSettingsForm.updateData(getWizardContext());
    }
    suggestProjectNameAndPath(settings.getDedicatedModuleDir(), myRootPathComponent.getPath());
  }

  public boolean validate() throws ConfigurationException {
    updateDataModel(); // needed to make 'exhaustive search' take an effect.
    return getBuilder().setRootDirectory(getWizardContext().getProject(), myRootPathComponent.getPath());
  }

  public void updateStep() {
    if (!myRootPathComponent.isPathChangedByUser()) {
      final VirtualFile rootDirectory = getBuilder().getRootDirectory();
      final String path;
      if (rootDirectory != null) {
        path = rootDirectory.getPath();
      }
      else {
        path = getWizardContext().getProjectFileDirectory();
      }
      myRootPathComponent.setPath(FileUtil.toSystemDependentName(path));
      myRootPathComponent.getPathComponent().selectAll();
    }
    myImportingSettingsForm.setData(getImportingSettings(), null);
  }

  public JComponent getPreferredFocusedComponent() {
    return myRootPathComponent.getPathComponent();
  }

  @Override
  public MavenProjectBuilder getBuilder() {
    return (MavenProjectBuilder)super.getBuilder();
  }

  private MavenGeneralSettings getGeneralSettings() {
    return getBuilder().getGeneralSettings();
  }

  private MavenImportingSettings getImportingSettings() {
    return getBuilder().getImportingSettings();
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.import.maven.page1";
  }

  class MavenEnvironmentConfigurable implements Configurable {
    MavenEnvironmentForm myForm = new MavenEnvironmentForm();

    @Nls
    public String getDisplayName() {
      return ProjectBundle.message("maven.import.environment.settings.title");
    }

    public JComponent createComponent() {
      return myForm.createComponent();
    }

    public boolean isModified() {
      return myForm.isModified(getGeneralSettings());
    }

    public void apply() throws ConfigurationException {
      myForm.setData(getGeneralSettings());
    }

    public void reset() {
      myForm.getData(getGeneralSettings());
    }
  }
}
