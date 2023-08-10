// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework;

import com.intellij.CommonBundle;
import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.RadioButtonEnumModel;
import kotlin.collections.ArraysKt;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinJvmBundle;
import org.jetbrains.kotlin.idea.projectConfiguration.CustomLibraryDescriptionWithDeferredConfig;
import org.jetbrains.kotlin.idea.projectConfiguration.JSLibraryStdDescription;
import org.jetbrains.kotlin.idea.projectConfiguration.JavaRuntimeLibraryDescription;
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle;
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter;
import org.jetbrains.kotlin.platform.JsPlatformKt;
import org.jetbrains.kotlin.platform.TargetPlatform;
import org.jetbrains.kotlin.platform.jvm.JvmPlatformKt;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

public class KotlinModuleSettingStep extends ModuleWizardStep {
    private static final Logger LOG = Logger.getInstance(KotlinModuleSettingStep.class);

    private final TargetPlatform targetPlatform;

    @Nullable
    private final ModuleWizardStep myJavaStep;

    private final CustomLibraryDescription customLibraryDescription;
    private final LibrariesContainer librariesContainer;
    private final Disposable myDisposable;

    private final boolean isNewProject;

    private LibraryOptionsPanel libraryOptionsPanel;
    private JPanel panel;

    private LibraryCompositionSettings libraryCompositionSettings;

    private final String basePath;

    public KotlinModuleSettingStep(
            TargetPlatform targetPlatform,
            ModuleBuilder moduleBuilder,
            @NotNull SettingsStep settingsStep,
            @NotNull WizardContext wizardContext
    ) {
        isNewProject = wizardContext.isCreatingNewProject();
        myDisposable = wizardContext.getDisposable();

        if (!(JvmPlatformKt.isJvm(targetPlatform))) {
            KotlinSdkType.Companion.setUpIfNeeded();
        }

        this.targetPlatform = targetPlatform;

        myJavaStep = JavaModuleType.getModuleType().modifyProjectTypeStep(settingsStep, moduleBuilder);

        basePath = moduleBuilder.getContentEntryPath();
        librariesContainer = LibrariesContainerFactory.createContainer(settingsStep.getContext().getProject());

        customLibraryDescription = getCustomLibraryDescription(settingsStep.getContext().getProject());

        moduleBuilder.addModuleConfigurationUpdater(createModuleConfigurationUpdater());

        settingsStep.addSettingsComponent(getComponent());
    }

    protected ModuleBuilder.ModuleConfigurationUpdater createModuleConfigurationUpdater() {
        return new ModuleBuilder.ModuleConfigurationUpdater() {
            @Override
            public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
                if (libraryCompositionSettings != null) {
                    libraryCompositionSettings.addLibraries(rootModel, new ArrayList<Library>(), librariesContainer);

                    if (customLibraryDescription instanceof CustomLibraryDescriptionWithDeferredConfig) {
                        ((CustomLibraryDescriptionWithDeferredConfig) customLibraryDescription).finishLibConfiguration(module, rootModel, isNewProject);
                    }
                }

                if (isNewProject) {
                    ProjectCodeStyleImporter.INSTANCE.apply(module.getProject(), KotlinStyleGuideCodeStyle.Companion.getINSTANCE());
                }
            }
        };
    }

    @Override
    public JComponent getComponent() {
        if (panel == null) {
            panel = new JPanel(new VerticalLayout(0));
            panel.setBorder(IdeBorderFactory.createTitledBorder(getLibraryLabelText()));
            panel.add(getLibraryPanel().getMainPanel());
        }
        return panel;
    }

    @NlsContexts.BorderTitle
    @NotNull
    protected String getLibraryLabelText() {
        if (JvmPlatformKt.isJvm(targetPlatform)) return KotlinJvmBundle.message("library.label.jvm");
        if (JsPlatformKt.isJs(targetPlatform)) return KotlinJvmBundle.message("library.label.javascript");
        throw new IllegalStateException("Only JS and JVM target are supported");
    }

    @NotNull
    protected CustomLibraryDescription getCustomLibraryDescription(@Nullable Project project) {
        if (JvmPlatformKt.isJvm(targetPlatform)) return new JavaRuntimeLibraryDescription(project);
        if (JsPlatformKt.isJs(targetPlatform)) return new JSLibraryStdDescription(project);
        throw new IllegalStateException("Only JS and JVM target are supported");
    }

    @Override
    public void updateDataModel() {
        libraryCompositionSettings = getLibraryPanel().apply();
        if (myJavaStep != null) {
            myJavaStep.updateDataModel();
        }
    }

    @Override
    public boolean validate() throws ConfigurationException {
        if (!(super.validate() && (myJavaStep == null || myJavaStep.validate()))) return false;

        Boolean selected = isLibrarySelected();
        if (selected != null && !selected) {
            if (Messages.showDialog(
                    KotlinJvmBundle.message("library.no.kotlin.library.question"),
                    KotlinJvmBundle.message("library.no.kotlin.library.title"),
                    new String[] {CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 1,
                    Messages.getWarningIcon()) != Messages.YES) {
                return false;
            }
        }

        return true;
    }

    protected LibraryOptionsPanel getLibraryPanel() {
        if (libraryOptionsPanel == null) {
            String baseDirPath = basePath != null ? FileUtil.toSystemIndependentName(basePath) : "";

            libraryOptionsPanel = new LibraryOptionsPanel(
                    customLibraryDescription,
                    baseDirPath,
                    FrameworkLibraryVersionFilter.ALL,
                    librariesContainer,
                    false);

            Disposer.register(myDisposable, libraryOptionsPanel);
        }

        return libraryOptionsPanel;
    }

    private Boolean isLibrarySelected() {
        try {
            // TODO: Get rid of this hack

            LibraryOptionsPanel panel = getLibraryPanel();
            Class<LibraryOptionsPanel> panelClass = LibraryOptionsPanel.class;

            Field modelField = ArraysKt.singleOrNull(
                    panelClass.getDeclaredFields(),
                    new Function1<>() {
                        @Override
                        public Boolean invoke(Field field) {
                            return RadioButtonEnumModel.class.isAssignableFrom(field.getType());
                        }
                    }
            );
            if (modelField == null) {
                LOG.error("There must be exactly one field of type RadioButtonEnumModel: " + Arrays.toString(panelClass.getDeclaredFields()));
                return false;
            }

            modelField.setAccessible(true);

            RadioButtonEnumModel enumModel = (RadioButtonEnumModel) modelField.get(panel);
            int ordinal = enumModel.getSelected().ordinal();
            if (ordinal == 0) {
                Field libComboboxField = ArraysKt.singleOrNull(
                        panelClass.getDeclaredFields(),
                        new Function1<>() {
                            @Override
                            public Boolean invoke(Field field) {
                                return JComboBox.class.isAssignableFrom(field.getType());
                            }
                        }
                );
                if (libComboboxField == null) {
                    LOG.error("There must be exactly one field of type JComboBox: " + Arrays.toString(panelClass.getDeclaredFields()));
                    return false;
                }

                libComboboxField.setAccessible(true);

                JComboBox combobox = (JComboBox) libComboboxField.get(panel);
                return combobox.getSelectedItem() != null;
            }

            return ordinal != 2;
        }
        catch (Exception e) {
            LOG.warn("Error in reflection", e);
        }

        return null;
    }
}
