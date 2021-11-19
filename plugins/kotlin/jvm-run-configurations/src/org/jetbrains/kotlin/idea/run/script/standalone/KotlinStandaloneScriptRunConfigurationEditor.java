// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run.script.standalone;

import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.execution.ui.ShortenCommandLineModeCombo;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinRunConfigurationsBundle;
import org.jetbrains.kotlin.parsing.KotlinParserDefinition;

import javax.swing.*;

public class KotlinStandaloneScriptRunConfigurationEditor extends SettingsEditor<KotlinStandaloneScriptRunConfiguration> implements PanelWithAnchor {
    private JPanel mainPanel;
    private CommonJavaParametersPanel commonProgramParameters;
    private JrePathEditor jrePathEditor;
    private TextFieldWithBrowseButton chooseScriptFileTextField;
    private LabeledComponent<TextFieldWithBrowseButton> chooseScriptFileComponent;
    private LabeledComponent<ShortenCommandLineModeCombo> shortenClasspathModeCombo;
    private JComponent anchor;

    public KotlinStandaloneScriptRunConfigurationEditor(Project project) {
        initChooseFileField(project);
        jrePathEditor.setDefaultJreSelector(DefaultJreSelector.projectSdk(project));
        anchor = UIUtil.mergeComponentsWithAnchor(chooseScriptFileComponent, commonProgramParameters, jrePathEditor, shortenClasspathModeCombo);
        shortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(project, jrePathEditor, () -> null, listener -> {}));
    }

    void initChooseFileField(Project project) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withFileFilter(file -> file.isDirectory() || KotlinParserDefinition.STD_SCRIPT_SUFFIX.equals(file.getExtension()))
                .withTreeRootVisible(true);

        chooseScriptFileTextField.addBrowseFolderListener(
                KotlinRunConfigurationsBundle.message("script.choose.file"),
                null, project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        );
    }

    @Override
    protected void resetEditorFrom(@NotNull KotlinStandaloneScriptRunConfiguration configuration) {
        commonProgramParameters.reset(configuration);
        String path = configuration.filePath;
        chooseScriptFileTextField.setText(path != null ? path : "");
        jrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
        shortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    }

    @Override
    protected void applyEditorTo(@NotNull KotlinStandaloneScriptRunConfiguration configuration) {
        commonProgramParameters.applyTo(configuration);
        configuration.setAlternativeJrePath(jrePathEditor.getJrePathOrName());
        configuration.setAlternativeJrePathEnabled(jrePathEditor.isAlternativeJreSelected());
        configuration.filePath = chooseScriptFileTextField.getText();
        configuration.setShortenCommandLine(shortenClasspathModeCombo.getComponent().getSelectedItem());
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return mainPanel;
    }

    @Override
    public JComponent getAnchor() {
        return anchor;
    }

    @Override
    public void setAnchor(JComponent anchor) {
        this.anchor = anchor;
        commonProgramParameters.setAnchor(anchor);
        jrePathEditor.setAnchor(anchor);
        chooseScriptFileComponent.setAnchor(anchor);
        shortenClasspathModeCombo.setAnchor(anchor);
    }

    private void createUIComponents() {
        chooseScriptFileComponent = new LabeledComponent<>();
        chooseScriptFileTextField = new TextFieldWithBrowseButton();
        chooseScriptFileComponent.setComponent(chooseScriptFileTextField);
    }
}
