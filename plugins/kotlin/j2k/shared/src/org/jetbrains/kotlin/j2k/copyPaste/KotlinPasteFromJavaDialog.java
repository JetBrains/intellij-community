// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.copyPaste;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions;
import org.jetbrains.kotlin.nj2k.KotlinNJ2KBundle;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("UnusedDeclaration")
public class KotlinPasteFromJavaDialog extends DialogWrapper {
    private JPanel panel;
    private JCheckBox donTShowThisCheckBox;
    private JLabel questionLabel;
    private JButton buttonOK;

    public KotlinPasteFromJavaDialog(@NotNull Project project, boolean isPlainText) {
        super(project, true);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        setTitle(KotlinNJ2KBundle.message("copy.title.convert.code.from.java"));
        if (isPlainText) {
            questionLabel.setText(
                    KotlinNJ2KBundle.message("copy.text.clipboard.content.seems.to.be.java.code.do.you.want.to.convert.it.to.kotlin"));
            //TODO: should we also use different set of settings?
        }
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }

    @Override
    public Container getContentPane() {
        return panel;
    }

    @Override
    protected @NotNull Action @NotNull [] createActions() {
        setOKButtonText(CommonBundle.getYesButtonText());
        setCancelButtonText(CommonBundle.getNoButtonText());
        return new Action[] {getOKAction(), getCancelAction()};
    }

    @Override
    protected void doOKAction() {
        if (donTShowThisCheckBox.isSelected()) {
            KotlinEditorOptions.getInstance().setDonTShowConversionDialog(true);
        }
        super.doOKAction();
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
