// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.containers.MultiMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.IntroduceTypeAliasDescriptor;
import org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.IntroduceTypeAliasImplKt;
import org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.KotlinIntroduceTypeAliasHandler;
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.KotlinSignatureComponent;
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringUtilKt.checkConflictsInteractively;

public class KotlinIntroduceTypeAliasDialog extends DialogWrapper {
    private JPanel contentPane;
    private TitledSeparator inputParametersPanel;
    private JComboBox<KtModifierKeywordToken> visibilityBox;
    private KotlinSignatureComponent signaturePreviewField;
    private JPanel aliasNamePanel;
    private NameSuggestionsField aliasNameField;
    private JLabel aliasNameLabel;
    private IntroduceTypeAliasParameterTablePanel parameterTablePanel;

    private final Project project;

    private final IntroduceTypeAliasDescriptor originalDescriptor;
    private IntroduceTypeAliasDescriptor currentDescriptor;

    private final Function1<KotlinIntroduceTypeAliasDialog, Unit> onAccept;

    public KotlinIntroduceTypeAliasDialog(
            @NotNull Project project,
            @NotNull IntroduceTypeAliasDescriptor originalDescriptor,
            @NotNull Function1<KotlinIntroduceTypeAliasDialog, Unit> onAccept) {
        super(project, true);

        this.project = project;
        this.originalDescriptor = originalDescriptor;
        this.currentDescriptor = originalDescriptor;
        this.onAccept = onAccept;

        setModal(true);
        setTitle(KotlinIntroduceTypeAliasHandler.getREFACTORING_NAME());
        init();
        update();
    }

    private void createUIComponents() {
        this.signaturePreviewField = new KotlinSignatureComponent("", project);
    }

    private boolean isVisibilitySectionAvailable() {
        return !getApplicableVisibilities().isEmpty();
    }

    private @NotNull List<KtModifierKeywordToken> getApplicableVisibilities() {
        return IntroduceTypeAliasImplKt.getApplicableVisibilities(originalDescriptor.getOriginalData());
    }

    private String getAliasName() {
        return KtPsiUtilKt.quoteIfNeeded(aliasNameField.getEnteredName());
    }

    private @Nullable KtModifierKeywordToken getVisibility() {
        if (!isVisibilitySectionAvailable()) return null;
        return (KtModifierKeywordToken) visibilityBox.getSelectedItem();
    }

    private boolean checkNames() {
        if (!KtPsiUtilKt.isIdentifier(getAliasName())) return false;
        if (parameterTablePanel != null) {
            for (IntroduceTypeAliasParameterTablePanel.TypeParameterInfo parameterInfo : parameterTablePanel.getSelectedTypeParameterInfos()) {
                if (!KtPsiUtilKt.isIdentifier(parameterInfo.getName())) return false;
            }
        }
        return true;
    }

    private void update() {
        this.currentDescriptor = createDescriptor();

        setOKActionEnabled(checkNames());
        signaturePreviewField.setText(IntroduceTypeAliasImplKt.generateTypeAlias(currentDescriptor, true).getText());
    }

    @Override
    protected void init() {
        super.init();

        visibilityBox.setModel(new DefaultComboBoxModel<>(getApplicableVisibilities().toArray(new KtModifierKeywordToken[0])));
        visibilityBox.setRenderer(
                new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(
                            JList list,
                            Object value,
                            int index,
                            boolean isSelected,
                            boolean cellHasFocus
                    ) {
                        @NlsSafe String tokenValue = value != null ? ((KtModifierKeywordToken) value).getValue() : null;
                        return super.getListCellRendererComponent(list, tokenValue, index, isSelected, cellHasFocus);
                    }
                }
        );

        aliasNameField = new NameSuggestionsField(new String[]{originalDescriptor.getName()}, project, KotlinFileType.INSTANCE);
        aliasNameField.addDataChangedListener(() -> update());
        aliasNamePanel.add(aliasNameField, BorderLayout.CENTER);
        aliasNameLabel.setLabelFor(aliasNameField);

        boolean enableVisibility = isVisibilitySectionAvailable();
        visibilityBox.setEnabled(enableVisibility);
        if (enableVisibility) {
            KtModifierKeywordToken defaultVisibility = originalDescriptor.getVisibility();
            if (defaultVisibility == null) {
                defaultVisibility = KtTokens.PUBLIC_KEYWORD;
            }
            visibilityBox.setSelectedItem(defaultVisibility);
        }
        visibilityBox.addItemListener(
                new ItemListener() {
                    @Override
                    public void itemStateChanged(@NotNull ItemEvent e) {
                        update();
                    }
                }
        );

        if (!originalDescriptor.getTypeParameters().isEmpty()) {
            parameterTablePanel = new IntroduceTypeAliasParameterTablePanel() {
                @Override
                protected void updateSignature() {
                    KotlinIntroduceTypeAliasDialog.this.update();
                }

                @Override
                protected void onEnterAction() {
                    doOKAction();
                }

                @Override
                protected void onCancelAction() {
                    doCancelAction();
                }
            };
            parameterTablePanel.init(originalDescriptor.getTypeParameters());

            inputParametersPanel.setText(KotlinBundle.message("text.type.parameters"));
            inputParametersPanel.setLabelFor(parameterTablePanel.getTable());
            inputParametersPanel.add(parameterTablePanel);
        }
        else {
            inputParametersPanel.setVisible(false);
        }
    }

    @Override
    protected void doOKAction() {
        MultiMap<PsiElement, String> conflicts = IntroduceTypeAliasImplKt.validate(currentDescriptor).getConflicts();
        checkConflictsInteractively(
                project,
                conflicts,
                new Function0<>() {
                    @Override
                    public Unit invoke() {
                        close(OK_EXIT_CODE);
                        return Unit.INSTANCE;
                    }
                },
                new Function0<>() {
                    @Override
                    public Unit invoke() {
                        KotlinIntroduceTypeAliasDialog.super.doOKAction();
                        return onAccept.invoke(KotlinIntroduceTypeAliasDialog.this);
                    }
                }
        );
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return aliasNameField;
    }

    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    protected @NotNull JComponent createContentPane() {
        return contentPane;
    }

    private @NotNull IntroduceTypeAliasDescriptor createDescriptor() {
        return originalDescriptor.copy(
                originalDescriptor.getOriginalData(),
                getAliasName(),
                getVisibility(),
                parameterTablePanel != null ? parameterTablePanel.getSelectedTypeParameters() : Collections.emptyList()
        );
    }

    public IntroduceTypeAliasDescriptor getCurrentDescriptor() {
        return currentDescriptor;
    }
}
