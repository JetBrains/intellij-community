// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.framework.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class CreateLibraryDialogWithModules extends DialogWrapper {

    private final ChooseModulePanel chooseModulePanel;

    public CreateLibraryDialogWithModules(
            @NotNull Project project,
            @NotNull KotlinProjectConfigurator configurator,
            @Nls @NotNull String title,
            @NotNull Collection<Module> excludeModules
    ) {
        super(project);
        chooseModulePanel = new ChooseModulePanel(project, configurator, excludeModules);
        setTitle(title);
        init();
    }

    public List<Module> getModulesToConfigure() {
        return chooseModulePanel.getModulesToConfigure();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return chooseModulePanel.getContentPane();
    }
}
