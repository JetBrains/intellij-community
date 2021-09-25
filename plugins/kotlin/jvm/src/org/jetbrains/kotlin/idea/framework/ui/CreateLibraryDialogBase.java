// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.SeparatorWithText;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.versions.KotlinRuntimeLibraryUtilKt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class CreateLibraryDialogBase extends DialogWrapper {
    protected JPanel contentPane;
    protected JLabel compilerTextLabel;
    protected JPanel chooseModulesPanelPlace;
    protected SeparatorWithText modulesSeparator;
    protected JPanel chooseLibraryPathPlace;

    protected final ChooseLibraryPathPanel pathPanel;

    public CreateLibraryDialogBase(
            @Nullable Project project,
            @NotNull String defaultPath,
            @Nls @NotNull String title,
            @Nls @NotNull String libraryCaption
    ) {
        super(project);

        setTitle(title);

        init();

        compilerTextLabel.setText(compilerTextLabel.getText() + " - " + KotlinRuntimeLibraryUtilKt.bundledRuntimeVersion());

        pathPanel = new ChooseLibraryPathPanel(defaultPath);
        pathPanel.addValidityListener(new ValidityListener() {
            @Override
            public void validityChanged(boolean isValid) {
                updateComponents();
            }
        });
        pathPanel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateComponents();
            }
        });
        chooseLibraryPathPlace.add(pathPanel.getContentPane(), BorderLayout.CENTER);

        modulesSeparator.setCaption(libraryCaption);
    }

    protected void updateComponents() {
        setOKActionEnabled(!pathPanel.hasErrors());
    }

    @Nullable
    public String getCopyIntoPath() {
        return chooseLibraryPathPlace.isVisible() ? pathPanel.getPath() : null;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

}
