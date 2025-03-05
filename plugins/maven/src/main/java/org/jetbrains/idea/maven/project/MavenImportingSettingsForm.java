// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemJdkComboBox;
import com.intellij.openapi.project.ExternalStorageConfigurationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.updateSettings.impl.LabelTextReplacingUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MavenImportingSettingsForm {
  private JPanel myPanel;

  private JCheckBox mySearchRecursivelyCheckBox;

  private JComboBox<String> myUpdateFoldersOnImportPhaseComboBox;
  private JCheckBox myUseMavenOutputCheckBox;
  private JCheckBox myDownloadSourcesCheckBox;
  private JCheckBox myDownloadDocsCheckBox;
  private JCheckBox myDownloadAnnotationsCheckBox;

  private JPanel myAdditionalSettingsPanel;
  private JComboBox<MavenImportingSettings.GeneratedSourcesFolder> myGeneratedSourcesComboBox;
  private JCheckBox myExcludeTargetFolderCheckBox;
  private JTextField myDependencyTypes;
  private JBTextField myVMOptionsForImporter;
  private ExternalSystemJdkComboBox myJdkForImporterComboBox;
  private JBLabel myImporterJdkWarning;
  private JCheckBox myAutoDetectCompilerCheckBox;

  private final ComponentValidator myImporterJdkValidator;
  private volatile boolean myMuteJdkValidation = false;

  public MavenImportingSettingsForm(Project project, @NotNull Disposable disposable) {
    myJdkForImporterComboBox.setProject(project);
    mySearchRecursivelyCheckBox.setVisible(project.isDefault());

    myUpdateFoldersOnImportPhaseComboBox.setModel(new DefaultComboBoxModel<>(MavenImportingSettings.UPDATE_FOLDERS_PHASES));

    myGeneratedSourcesComboBox.setModel(new EnumComboBoxModel<>(MavenImportingSettings.GeneratedSourcesFolder.class));
    myGeneratedSourcesComboBox.setRenderer(BuilderKt.textListCellRenderer("", value -> value.getTitle()));

    LabelTextReplacingUtil.replaceText(myPanel);
    myAutoDetectCompilerCheckBox.setVisible(Registry.is("maven.import.compiler.arguments", true));
    myJdkForImporterComboBox.setHighlightInternalJdk(false);
    ActionListener validatorListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateImporterJDK();
      }
    };
    myJdkForImporterComboBox.addActionListener(validatorListener);

    myImporterJdkValidator = new ComponentValidator(disposable)
      .withValidator(() -> {
        if (JavaSdkVersionUtil.isAtLeast(myJdkForImporterComboBox.getSelectedJdk(), JavaSdkVersion.JDK_1_8)) {
          return null;
        } else {
          return new ValidationInfo(MavenConfigurableBundle.message("maven.settings.importing.jdk.too.old.error"), myJdkForImporterComboBox);
        }
      })
      .installOn(myJdkForImporterComboBox);

    myImporterJdkWarning.setVisible(false);
  }

  private void updateModuleDirControls() {
    validateImporterJDK();
  }

  public String getDefaultModuleDir() {
    return "";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public void getData(@NotNull MavenImportingSettings data) {
    data.setLookForNested(mySearchRecursivelyCheckBox.isSelected());
    LookForNestedToggleAction.setSelected(mySearchRecursivelyCheckBox.isSelected());

    data.setExcludeTargetFolder(myExcludeTargetFolderCheckBox.isSelected());
    data.setUseMavenOutput(myUseMavenOutputCheckBox.isSelected());

    data.setUpdateFoldersOnImportPhase((String)myUpdateFoldersOnImportPhaseComboBox.getSelectedItem());
    data.setGeneratedSourcesFolder((MavenImportingSettings.GeneratedSourcesFolder)myGeneratedSourcesComboBox.getSelectedItem());

    data.setDownloadSourcesAutomatically(myDownloadSourcesCheckBox.isSelected());
    data.setDownloadDocsAutomatically(myDownloadDocsCheckBox.isSelected());
    data.setDownloadAnnotationsAutomatically(myDownloadAnnotationsCheckBox.isSelected());
    data.setAutoDetectCompiler(myAutoDetectCompilerCheckBox.isSelected());

    data.setVmOptionsForImporter(myVMOptionsForImporter.getText());
    data.setJdkForImporter(myJdkForImporterComboBox.getSelectedValue());

    data.setDependencyTypes(myDependencyTypes.getText());
  }

  public void setData(MavenImportingSettings data, @Nullable Project project) {
    mySearchRecursivelyCheckBox.setSelected(LookForNestedToggleAction.isSelected());

    myExcludeTargetFolderCheckBox.setSelected(data.isExcludeTargetFolder());
    myUseMavenOutputCheckBox.setSelected(data.isUseMavenOutput());

    myUpdateFoldersOnImportPhaseComboBox.setSelectedItem(data.getUpdateFoldersOnImportPhase());
    myGeneratedSourcesComboBox.setSelectedItem(data.getGeneratedSourcesFolder());

    myDownloadSourcesCheckBox.setSelected(data.isDownloadSourcesAutomatically());
    myDownloadDocsCheckBox.setSelected(data.isDownloadDocsAutomatically());
    myDownloadAnnotationsCheckBox.setSelected(data.isDownloadAnnotationsAutomatically());
    myAutoDetectCompilerCheckBox.setSelected(data.isAutoDetectCompiler());

    myDependencyTypes.setText(data.getDependencyTypes());

    myVMOptionsForImporter.setText(data.getVmOptionsForImporter());
    skipValidationDuring(() -> myJdkForImporterComboBox.refreshData(data.getJdkForImporter()));

    updateModuleDirControls();
  }


  private void skipValidationDuring(Runnable r) {
    myMuteJdkValidation = true;
    try {
      r.run();
    } finally {
      myMuteJdkValidation = false;
      validateImporterJDK();
    }
  }

  private static boolean isCurrentlyStoredExternally(@Nullable Project project) {
    return project == null || ExternalStorageConfigurationManager.getInstance(project).isEnabled();
  }

  public boolean isModified(@NotNull MavenImportingSettings settings) {
    MavenImportingSettings formData = new MavenImportingSettings();
    getData(formData);
    return !formData.equals(settings);
  }

  public JPanel getAdditionalSettingsPanel() {
    return myAdditionalSettingsPanel;
  }

  private void validateImporterJDK() {
    if (myMuteJdkValidation) {
      return;
    }
    myImporterJdkValidator.revalidate();
    if (myImporterJdkValidator.getValidationInfo() == null) {
      myImporterJdkWarning.setVisible(false);
    } else {
      myImporterJdkWarning.setVisible(true);
    }
  }
}
