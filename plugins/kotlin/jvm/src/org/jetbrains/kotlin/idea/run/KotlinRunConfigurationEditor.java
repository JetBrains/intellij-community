// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.ui.*;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class KotlinRunConfigurationEditor extends SettingsEditor<KotlinRunConfiguration> implements PanelWithAnchor {
    private JPanel mainPanel;
    private LabeledComponent<EditorTextFieldWithBrowseButton> mainClass;

    private CommonJavaParametersPanel commonProgramParameters;
    private LabeledComponent<ModuleDescriptionsComboBox> moduleChooser;
    private JrePathEditor jrePathEditor;
    private LabeledComponent<ShortenCommandLineModeCombo> shortenClasspathModeCombo;

    private final ConfigurationModuleSelector moduleSelector;
    private JComponent anchor;

    private final Project project;

    private static ClassBrowser createApplicationClassBrowser(
            Project project,
            ConfigurationModuleSelector moduleSelector
    ) {
        ClassFilter applicationClass = new ClassFilter() {
            @Override
            public boolean isAccepted(PsiClass aClass) {
                return aClass instanceof KtLightClass && ConfigurationUtil.MAIN_CLASS.value(aClass) && findMainMethod(aClass) != null;
            }

            @Nullable
            private PsiMethod findMainMethod(PsiClass aClass) {
                return ReadAction.compute(() -> PsiMethodUtil.findMainMethod(aClass));
            }
        };
        return new ClassBrowser.MainClassBrowser(project, moduleSelector, ExecutionBundle.message("choose.main.class.dialog.title")) {
            @Override
            protected ClassFilter createFilter(Module module) {
                return applicationClass;
            }
        };
    }

    public KotlinRunConfigurationEditor(Project project) {
        this.project = project;
        moduleSelector = new ConfigurationModuleSelector(project, moduleChooser.getComponent());
        jrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(moduleChooser.getComponent(), false));
        commonProgramParameters.setModuleContext(moduleSelector.getModule());
        moduleChooser.getComponent().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commonProgramParameters.setModuleContext(moduleSelector.getModule());
            }
        });
        createApplicationClassBrowser(project, moduleSelector).setField(mainClass.getComponent());
        anchor = UIUtil.mergeComponentsWithAnchor(mainClass, commonProgramParameters, jrePathEditor, jrePathEditor, moduleChooser,
                                                  shortenClasspathModeCombo);
        shortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(project, jrePathEditor, moduleChooser.getComponent()));
    }

    @Override
    protected void applyEditorTo(KotlinRunConfiguration configuration) {
        commonProgramParameters.applyTo(configuration);
        moduleSelector.applyTo(configuration);

        String className = mainClass.getComponent().getText();
        PsiClass aClass = moduleSelector.findClass(className);

        configuration.setRunClass(aClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(aClass) : className);
        configuration.setAlternativeJrePath(jrePathEditor.getJrePathOrName());
        configuration.setAlternativeJrePathEnabled(jrePathEditor.isAlternativeJreSelected());
        configuration.setShortenCommandLine(shortenClasspathModeCombo.getComponent().getSelectedItem());
    }

    @Override
    protected void resetEditorFrom(KotlinRunConfiguration configuration) {
        commonProgramParameters.reset(configuration);
        moduleSelector.reset(configuration);
        String runClass = configuration.getRunClass();
        mainClass.getComponent().setText(runClass != null ? runClass.replaceAll("\\$", "\\.") : "");
        jrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
        shortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return mainPanel;
    }

    private void createUIComponents() {
        mainClass = new LabeledComponent<>();
        mainClass.setComponent(new EditorTextFieldWithBrowseButton(project, true, (declaration, place) -> {
            if (declaration instanceof KtLightClass) {
                KtLightClass aClass = (KtLightClass)declaration;
                if (ConfigurationUtil.MAIN_CLASS.value(aClass)
                    && (PsiMethodUtil.findMainMethod(aClass) != null || place.getParent() != null)
                    && moduleSelector.findClass(((PsiClass)declaration).getQualifiedName()) != null) {
                    return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
                }
            }
            return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
        }));
    }

    @Override
    public JComponent getAnchor() {
        return anchor;
    }

    @Override
    public void setAnchor(JComponent anchor) {
        this.anchor = anchor;
        mainClass.setAnchor(anchor);
        commonProgramParameters.setAnchor(anchor);
        jrePathEditor.setAnchor(anchor);
        moduleChooser.setAnchor(anchor);
        shortenClasspathModeCombo.setAnchor(anchor);
    }
}
