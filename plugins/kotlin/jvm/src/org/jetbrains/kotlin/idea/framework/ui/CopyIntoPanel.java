// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinJvmBundle;
import org.jetbrains.kotlin.idea.core.util.UiUtilsKt;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class CopyIntoPanel {
    private JPanel contentPane;
    private TextFieldWithBrowseButton copyIntoField;
    private JLabel copyIntoLabel;

    private final EventDispatcher<ValidityListener> validityDispatcher = EventDispatcher.create(ValidityListener.class);

    private boolean hasErrorsState;

    public CopyIntoPanel(@Nullable Project project, @NotNull String defaultPath) {
        this(project, defaultPath, null);
    }

    public CopyIntoPanel(@Nullable Project project, @NotNull String defaultPath, @Nullable String labelText) {
        copyIntoField.addBrowseFolderListener(
                KotlinJvmBundle.message("copy.into.title"),
                KotlinJvmBundle.message("copy.into.description"),
                project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        UiUtilsKt.onTextChange(
                copyIntoField.getTextField(),
                (DocumentEvent e) -> {
                    updateComponents();
                    return Unit.INSTANCE;
                }
        );

        if (labelText != null) {
            String text = labelText.replace("&", "");
            int mnemonicIndex = labelText.indexOf("&");
            char mnemonicChar = mnemonicIndex != -1 && (mnemonicIndex + 1) < labelText.length() ? labelText.charAt(mnemonicIndex + 1) : 0;

            copyIntoLabel.setText(text);
            copyIntoLabel.setDisplayedMnemonic(mnemonicChar);
            copyIntoLabel.setDisplayedMnemonicIndex(mnemonicIndex);
        }
        else {
            copyIntoLabel.setVisible(false);
        }

        copyIntoLabel.setLabelFor(copyIntoField.getTextField());
        copyIntoField.getTextField().setText(defaultPath);

        copyIntoField.getTextField().setColumns(40);

        updateComponents();
    }

    public JComponent getContentPane() {
        return contentPane;
    }

    public void addValidityListener(ValidityListener listener) {
        validityDispatcher.addListener(listener);
    }

    @Nullable
    public String getPath() {
        return copyIntoField.isEnabled() ? copyIntoField.getText().trim() : null;
    }

    private void updateComponents() {
        boolean isError = false;

        copyIntoLabel.setForeground(JBColor.foreground());
        if (copyIntoField.isEnabled()) {
            if (copyIntoField.getText().trim().isEmpty()) {
                copyIntoLabel.setForeground(JBColor.red);
                isError = true;
            }
        }

        if (isError != hasErrorsState) {
            hasErrorsState = isError;
            validityDispatcher.getMulticaster().validityChanged(isError);
        }
    }

    public void setEnabled(boolean enabled) {
        copyIntoField.setEnabled(enabled);
    }

    public boolean hasErrors() {
        return hasErrorsState;
    }

    public void setLabelWidth(int width) {
        copyIntoLabel.setPreferredSize(new Dimension(width, -1));
    }
}
