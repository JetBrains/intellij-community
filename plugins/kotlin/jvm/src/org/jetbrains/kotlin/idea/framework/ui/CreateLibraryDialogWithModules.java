// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator;

import java.awt.*;
import java.util.Collection;
import java.util.List;

public class CreateLibraryDialogWithModules extends CreateLibraryDialogBase {

    private final ChooseModulePanel chooseModulePanel;

    public CreateLibraryDialogWithModules(
            @NotNull Project project,
            @NotNull KotlinProjectConfigurator configurator,
            @NotNull String defaultPath,
            boolean showPathPanel,
            @NotNull String title,
            @NotNull String libraryCaption,
            @NotNull Collection<Module> excludeModules
    ) {
        super(project, defaultPath, title, libraryCaption);

        chooseModulePanel = new ChooseModulePanel(project, configurator, excludeModules);
        chooseModulesPanelPlace.add(chooseModulePanel.getContentPane(), BorderLayout.CENTER);

        chooseLibraryPathPlace.setVisible(showPathPanel);
        modulesSeparator.setVisible(showPathPanel);

        updateComponents();
    }

    public List<Module> getModulesToConfigure() {
        return chooseModulePanel.getModulesToConfigure();
    }
}
