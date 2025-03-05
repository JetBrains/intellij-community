// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.application.ClassEditorField;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.ui.*;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.LabeledComponentNoThrow;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.idea.projectView.KtDeclarationTreeNode;
import org.jetbrains.kotlin.idea.projectView.KtFileTreeNode;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.psiUtil.PsiUtilsKt;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public final class KotlinRunConfigurationEditor extends SettingsEditor<KotlinRunConfiguration> implements PanelWithAnchor {
  private JPanel mainPanel;
  private LabeledComponent<ClassEditorField> mainClass;

  private CommonJavaParametersPanel commonProgramParameters;
  private LabeledComponentNoThrow<ModuleDescriptionsComboBox> moduleChooser;
  private JrePathEditor jrePathEditor;
  private LabeledComponent<ShortenCommandLineModeCombo> shortenClasspathModeCombo;

    private JComponent anchor;

    private final ConfigurationModuleSelector moduleSelector;
    private final Project project;

    private static ClassBrowser createApplicationClassBrowser(
            Project project,
            Computable<? extends Module> moduleSelector,
            LabeledComponent<ModuleDescriptionsComboBox> moduleChooser
    ) {
        ClassFilter applicationClass = new ClassFilter() {
            @Override
            public boolean isAccepted(PsiClass aClass) {
                return aClass instanceof KtLightClass && ConfigurationUtil.MAIN_CLASS.value(aClass) && findMainMethod(aClass) != null;
            }

            private static @Nullable PsiMethod findMainMethod(PsiClass aClass) {
                return ReadAction.compute(() -> PsiMethodUtil.findMainMethod(aClass));
            }
        };
        return new ClassBrowser.MainClassBrowser(project, moduleSelector, ExecutionBundle.message("choose.main.class.dialog.title")) {
            @Override
            protected ClassFilter createFilter(Module module) {
                return applicationClass;
            }

            @Override
            protected void onClassChosen(@NotNull PsiClass psiClass) {
                Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
                if (module != null && ModuleTypeManager.getInstance().isClasspathProvider(ModuleType.get(module))) {
                    moduleChooser.getComponent().setSelectedModule(module);
                }
            }

            @Override
            protected TreeClassChooser createClassChooser(ClassFilter.ClassFilterWithScope classFilter) {
                Project project = getProject();
                return new TreeJavaClassChooserDialog(myTitle, project, classFilter.getScope(), classFilter, null, null, true) {
                    @Override
                    protected @Nullable PsiClass getSelectedFromTreeUserObject(DefaultMutableTreeNode node) {
                        Object userObject = node.getUserObject();
                        if (userObject instanceof ClassTreeNode treeNode) {
                            return treeNode.getPsiClass();
                        }
                        KtElement ktElement = null;
                        if (userObject instanceof KtFileTreeNode treeNode) {
                            ktElement = treeNode.getKtFile();
                        }
                        if (userObject instanceof KtDeclarationTreeNode treeNode) {
                            ktElement = treeNode.getDeclaration();
                        }

                        if (ktElement != null) {
                            List<PsiNamedElement> elements = LightClassUtilsKt.toLightElements(ktElement);
                            if (!elements.isEmpty()) {
                                PsiNamedElement element = elements.get(0);
                                PsiClass parent = PsiUtilsKt.getParentOfTypes(element, false, PsiClass.class);
                                if (parent != null) {
                                    return parent;
                                }
                            }
                        }
                        return null;
                    }
                };
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
        anchor = UIUtil.mergeComponentsWithAnchor(mainClass, commonProgramParameters, jrePathEditor, jrePathEditor, moduleChooser,
                                                  shortenClasspathModeCombo);
        shortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(project, jrePathEditor, moduleChooser.getComponent()));
    }

    @Override
    protected void applyEditorTo(@NotNull KotlinRunConfiguration configuration) {
        commonProgramParameters.applyTo(configuration);
        moduleSelector.applyTo(configuration);

        configuration.setRunClass(mainClass.getComponent().getClassName());
        configuration.setAlternativeJrePath(jrePathEditor.getJrePathOrName());
        configuration.setAlternativeJrePathEnabled(jrePathEditor.isAlternativeJreSelected());
        configuration.setShortenCommandLine(shortenClasspathModeCombo.getComponent().getSelectedItem());
    }

    @Override
    protected void resetEditorFrom(@NotNull KotlinRunConfiguration configuration) {
        commonProgramParameters.reset(configuration);
        moduleSelector.reset(configuration);
        String runClass = configuration.getRunClass();
        mainClass.getComponent().setText(runClass != null ? runClass.replaceAll("\\$", "\\.") : "");
        jrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
        shortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return mainPanel;
    }

    private void createUIComponents() {
        mainClass = new LabeledComponent<>();
        mainClass.setComponent(ClassEditorField.createClassField(project, () -> moduleSelector.getModule(), (declaration, place) -> {
            if (declaration instanceof KtLightClass aClass) {
              if (ConfigurationUtil.MAIN_CLASS.value(aClass)
                    && (PsiMethodUtil.findMainMethod(aClass) != null || place.getParent() != null)
                    && moduleSelector.findClass(((PsiClass)declaration).getQualifiedName()) != null) {
                    return JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
                }
            }
            return JavaCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE;
        },  createApplicationClassBrowser(project, () -> moduleSelector.getModule(), moduleChooser)));
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
