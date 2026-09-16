// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MavenImportingSettingsForm {

  private final MavenImportingSettings settings;
  private final MavenImportingSettingsUi ui;

  private final ComponentValidator myImporterJdkValidator;
  private volatile boolean myMuteJdkValidation = false;

  public MavenImportingSettingsForm(Project project, @NotNull Disposable disposable, @NotNull MavenImportingSettingsUi ui) {
    settings = MavenProjectsManager.getInstance(project).getImportingSettings();
    this.ui = ui;

    ui.jdkForImporterComboBox.setProject(project);
    ui.searchRecursivelyCheckBox.setVisible(project.isDefault());
    ui.jdkForImporterComboBox.setHighlightInternalJdk(false);
    ActionListener validatorListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateImporterJDK();
      }
    };
    ui.jdkForImporterComboBox.addActionListener(validatorListener);

    myImporterJdkValidator = new ComponentValidator(disposable)
      .withValidator(() -> {
        if (JavaSdkVersionUtil.isAtLeast(ui.jdkForImporterComboBox.getSelectedJdk(), JavaSdkVersion.JDK_17)) {
          return null;
        }
        var settings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings();
        MavenHomeType type = settings.getGeneralSettings().getMavenHomeType();
        if (type instanceof StaticResolvedMavenHomeType staticResolvedMavenHomeType) {
          var version = MavenUtil.getMavenVersion(staticResolvedMavenHomeType);
          if (version != null && version.startsWith("4")) {
            return new ValidationInfo(MavenConfigurableBundle.message("maven.settings.importing.jdk.too.old.error.4"),
                                      ui.jdkForImporterComboBox);
          }
        }
        if (!JavaSdkVersionUtil.isAtLeast(ui.jdkForImporterComboBox.getSelectedJdk(), JavaSdkVersion.JDK_1_8)) {
          return new ValidationInfo(MavenConfigurableBundle.message("maven.settings.importing.jdk.too.old.error"),
                                    ui.jdkForImporterComboBox);
        }
        return null;
      })
      .installOn(ui.jdkForImporterComboBox);

    ui.importerJdkWarning.setVisible(false);
  }

  private void updateModuleDirControls() {
    validateImporterJDK();
  }

  public DialogPanel createComponent() {
    return ui.panel;
  }

  public void getData(@NotNull MavenImportingSettings data) {
    data.setLookForNested(ui.searchRecursivelyCheckBox.isSelected());
    LookForNestedToggleAction.setSelected(ui.searchRecursivelyCheckBox.isSelected());

    data.setExcludeTargetFolder(ui.excludeTargetFolderCheckBox.isSelected());
    data.setUseMavenOutput(ui.useMavenOutputCheckBox.isSelected());

    data.setUpdateFoldersOnImportPhase((String)ui.updateFoldersOnImportPhaseComboBox.getSelectedItem());
    data.setGeneratedSourcesFolder((MavenImportingSettings.GeneratedSourcesFolder)ui.generatedSourcesComboBox.getSelectedItem());

    data.setDownloadSourcesAutomatically(ui.downloadSourcesCheckBox.isSelected());
    data.setDownloadDocsAutomatically(ui.downloadDocsCheckBox.isSelected());
    data.setDownloadAnnotationsAutomatically(ui.downloadAnnotationsCheckBox.isSelected());
    data.setAutoDetectCompiler(ui.autoDetectCompilerCheckBox.isSelected());
    data.setRunPluginsCompatibilityOnSyncAndBuild(ui.runPluginsCompat.isSelected());

    data.setVmOptionsForImporter(ui.vmOptionsForImporter.getText());
    data.setJdkForImporter(ui.jdkForImporterComboBox.getSelectedValue());

    data.setDependencyTypes(ui.dependencyTypes.getText());
  }

  public void apply() {
    getData(settings);
  }

  public void reset() {
    ui.searchRecursivelyCheckBox.setSelected(LookForNestedToggleAction.isSelected());

    ui.excludeTargetFolderCheckBox.setSelected(settings.isExcludeTargetFolder());
    ui.useMavenOutputCheckBox.setSelected(settings.isUseMavenOutput());

    ui.updateFoldersOnImportPhaseComboBox.setSelectedItem(settings.getUpdateFoldersOnImportPhase());
    ui.generatedSourcesComboBox.setSelectedItem(settings.getGeneratedSourcesFolder());

    ui.downloadSourcesCheckBox.setSelected(settings.isDownloadSourcesAutomatically());
    ui.downloadDocsCheckBox.setSelected(settings.isDownloadDocsAutomatically());
    ui.downloadAnnotationsCheckBox.setSelected(settings.isDownloadAnnotationsAutomatically());
    ui.autoDetectCompilerCheckBox.setSelected(settings.isAutoDetectCompiler());
    ui.runPluginsCompat.setSelected(settings.isRunPluginsCompatibilityOnSyncAndBuild());

    ui.dependencyTypes.setText(settings.getDependencyTypes());

    ui.vmOptionsForImporter.setText(settings.getVmOptionsForImporter());
    skipValidationDuring(() -> ui.jdkForImporterComboBox.refreshData(settings.getJdkForImporter()));

    updateModuleDirControls();
  }


  private void skipValidationDuring(Runnable r) {
    myMuteJdkValidation = true;
    try {
      r.run();
    }
    finally {
      myMuteJdkValidation = false;
      validateImporterJDK();
    }
  }

  public boolean isModified() {
    MavenImportingSettings formData = new MavenImportingSettings();
    getData(formData);
    return !formData.equals(settings);
  }

  private void validateImporterJDK() {
    if (myMuteJdkValidation) {
      return;
    }
    myImporterJdkValidator.revalidate();
    ui.importerJdkWarning.setVisible(myImporterJdkValidator.getValidationInfo() != null);
  }
}
