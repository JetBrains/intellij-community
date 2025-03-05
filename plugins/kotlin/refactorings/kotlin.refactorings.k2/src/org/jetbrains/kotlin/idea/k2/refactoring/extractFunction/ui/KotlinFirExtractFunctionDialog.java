// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analysis.api.types.KaType;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.*;
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringUtilKt;
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractUtilKt;
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IReplacement;
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ParameterReplacement;
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.KotlinSignatureComponent;
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.psi.KtSimpleNameExpression;
import org.jetbrains.kotlin.psi.KtTypeCodeFragment;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KotlinFirExtractFunctionDialog extends DialogWrapper {
    private JPanel contentPane;
    private TitledSeparator inputParametersPanel;
    private JComboBox visibilityBox;
    private KotlinSignatureComponent signaturePreviewField;
    private JPanel functionNamePanel;
    private NameSuggestionsField functionNameField;
    private JLabel functionNameLabel;
    private JComboBox<KtTypeCodeFragment> returnTypeBox;
    private JPanel returnTypePanel;
    private FirExtractFunctionParameterTablePanel parameterTablePanel;

    private final Project project;

    private final ExtractableCodeDescriptorWithConflicts originalDescriptor;

    private final Function1<ExtractableCodeDescriptor, Unit> onAccept;

    public KotlinFirExtractFunctionDialog(
            @NotNull Project project,
            @NotNull ExtractableCodeDescriptorWithConflicts originalDescriptor,
            @NotNull Function1<ExtractableCodeDescriptor, Unit> onAccept
    ) {
        super(project, true);

        this.project = project;
        this.originalDescriptor = originalDescriptor;
        this.onAccept = onAccept;

        setModal(true);
        setTitle(KotlinBundle.message("extract.function"));
        init();
        update();
    }

    private void createUIComponents() {
        this.signaturePreviewField = new KotlinSignatureComponent("", project);
    }

    private boolean isVisibilitySectionAvailable() {
        return ExtractUtilKt.isVisibilityApplicable(originalDescriptor.getDescriptor().getExtractionData());
    }

    private String getFunctionName() {
        return KtPsiUtilKt.quoteIfNeeded(functionNameField.getEnteredName());
    }

    private @Nullable KtModifierKeywordToken getVisibility() {
        if (!isVisibilitySectionAvailable()) return null;

        KtModifierKeywordToken value = (KtModifierKeywordToken) visibilityBox.getSelectedItem();
        return KtTokens.DEFAULT_VISIBILITY_KEYWORD.equals(value) ? null : value;
    }

    private boolean checkNames() {
        if (!KtPsiUtilKt.isIdentifier(getFunctionName())) return false;
        for (FirExtractFunctionParameterTablePanel.ParameterInfo parameterInfo : parameterTablePanel.getSelectedParameterInfos()) {
            if (!KtPsiUtilKt.isIdentifier(parameterInfo.getName())) return false;
        }
        return true;
    }

    private void update() {
        setOKActionEnabled(checkNames());
        ReadAction.nonBlocking(() ->
                                       PresentationUtilKt.getSignaturePreview(originalDescriptor.getDescriptor(),
                                                                              getFunctionName(),
                                                                              getVisibility(),
                                                                              parameterTablePanel.getSelectedReceiverInfo(),
                                                                              parameterTablePanel.getSelectedParameterInfos(),
                                                                              ((KtTypeCodeFragment) returnTypeBox.getSelectedItem()),
                                                                              originalDescriptor.getDescriptor().getContext())
                )
                .expireWith(getDisposable())
                .finishOnUiThread(ModalityState.stateForComponent(signaturePreviewField), preview -> signaturePreviewField.setText(preview))
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    @Override
    protected void init() {
        super.init();

        ExtractableCodeDescriptor extractableCodeDescriptor = originalDescriptor.getDescriptor();

        functionNameField = new NameSuggestionsField(
                //TODO ArrayUtil.toStringArray(extractableCodeDescriptor.getSuggestedNames()),
                // without type pointers (see KT-60484), combobox with names fires events which invalidate types precalculated by first phase of refactoring
                new String[] { extractableCodeDescriptor.getName() },
                project,
                PlainTextFileType.INSTANCE
        );
        functionNameField.addDataChangedListener(() -> update());
        functionNamePanel.add(functionNameField, BorderLayout.CENTER);
        functionNameLabel.setLabelFor(functionNameField);

        KtElement context = originalDescriptor.getDescriptor().getContext();
        List<KaType> possibleReturnTypes = ExtractableCodeDescriptorKt.getPossibleReturnTypes(extractableCodeDescriptor.getControlFlow());
        if (!possibleReturnTypes.isEmpty()) {
            List<KtTypeCodeFragment> fragments = ContainerUtil.map(possibleReturnTypes,
                                                                   t -> new KtPsiFactory(project).createTypeCodeFragment(
                                                                           PresentationUtilKt.render(t, context), context));
            DefaultComboBoxModel<KtTypeCodeFragment> returnTypeBoxModel =
                    new DefaultComboBoxModel<>(fragments.toArray(new KtTypeCodeFragment[0]));
            returnTypeBox.setModel(returnTypeBoxModel);
            returnTypeBox.setRenderer(
                    new DefaultListCellRenderer() {
                        @Override
                        public @NotNull Component getListCellRendererComponent(
                                JList list,
                                Object value,
                                int index,
                                boolean isSelected,
                                boolean cellHasFocus
                        ) {
                            @NlsSafe
                            String text = ((KtTypeCodeFragment) value).getText();
                            setText(text);
                            return this;
                        }
                    }
            );
            returnTypeBox.addItemListener(
                    new ItemListener() {
                        @Override
                        public void itemStateChanged(@NotNull ItemEvent e) {
                            update();
                        }
                    }
            );
        } else {
            returnTypePanel.getParent().remove(returnTypePanel);
        }

        visibilityBox.setModel(new DefaultComboBoxModel(KtTokens.VISIBILITY_MODIFIERS.getTypes()));

        boolean enableVisibility = isVisibilitySectionAvailable();
        visibilityBox.setEnabled(enableVisibility);
        if (enableVisibility) {
            KtModifierKeywordToken defaultVisibility = extractableCodeDescriptor.getVisibility();
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

        parameterTablePanel = new FirExtractFunctionParameterTablePanel() {
            @Override
            public @NotNull KtElement getContext() {
                return context;
            }

            @Override
            protected void updateSignature() {
                KotlinFirExtractFunctionDialog.this.update();
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
        parameterTablePanel.init(extractableCodeDescriptor.getReceiverParameter(), extractableCodeDescriptor.getParameters());

        inputParametersPanel.setText(KotlinBundle.message("text.parameters"));
        inputParametersPanel.setLabelFor(parameterTablePanel.getTable());
        inputParametersPanel.add(parameterTablePanel);
    }

    @Override
    protected void doOKAction() {
        ExtractableCodeDescriptorWithConflicts result = PresentationUtilKt.validate(originalDescriptor.getDescriptor(),
                                                                                    getFunctionName(),
                                                                                    getVisibility(),
                                                                                    parameterTablePanel.getSelectedReceiverInfo(),
                                                                                    parameterTablePanel.getSelectedParameterInfos(),
                                                                                    ((KtTypeCodeFragment) returnTypeBox.getSelectedItem()),
                                                                                    originalDescriptor.getDescriptor().getContext());

        MultiMap<PsiElement, String> conflicts = result.getConflicts();
        conflicts.values().removeAll(originalDescriptor.getConflicts().values());

        KotlinCommonRefactoringUtilKt.checkConflictsInteractively(
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
                        KotlinFirExtractFunctionDialog.super.doOKAction();
                        return onAccept.invoke(result.getDescriptor());
                    }
                }
        );
        close(OK_EXIT_CODE);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return functionNameField.getFocusableComponent();
    }

    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    protected @NotNull JComponent createContentPane() {
        return contentPane;
    }

    public static ExtractableCodeDescriptor createNewDescriptor(
            @NotNull ExtractableCodeDescriptor originalDescriptor,
            @NotNull String newName,
            @Nullable KtModifierKeywordToken newVisibility,
            @Nullable FirExtractFunctionParameterTablePanel.ParameterInfo newReceiverInfo,
            @NotNull List<FirExtractFunctionParameterTablePanel.ParameterInfo> newParameterInfos,
            @Nullable KaType returnType
    ) {
        Map<Parameter, Parameter> oldToNewParameters = new LinkedHashMap<>();
        for (FirExtractFunctionParameterTablePanel.ParameterInfo parameterInfo : newParameterInfos) {
            oldToNewParameters.put(parameterInfo.getOriginalParameter(), parameterInfo.toParameter());
        }
        Parameter originalReceiver = originalDescriptor.getReceiverParameter();
        Parameter newReceiver = newReceiverInfo != null ? newReceiverInfo.toParameter() : null;
        if (originalReceiver != null && newReceiver != null) {
            oldToNewParameters.put(originalReceiver, newReceiver);
        }

        ExtractionData data = originalDescriptor.getExtractionData();
        var newReplacementMap = MultiMap.<KtSimpleNameExpression, IReplacement<KaType>>create();
        originalDescriptor.getReplacementMap()
                .entrySet()
                .forEach(entry -> {
                    List<IReplacement<KaType>> newReplacement = ContainerUtil.map(entry.getValue(), p -> {
                        if (p instanceof ParameterReplacement<KaType> r) {
                            Parameter newParameter = oldToNewParameters.get(r.getParameter());
                            return newParameter != null ? r.copy(newParameter) : p;
                        } else {
                            return p;
                        }
                    });
                    newReplacementMap.putValues(entry.getKey(), newReplacement);
                });
        return new ExtractableCodeDescriptor(originalDescriptor.getContext(),
                                             data,
                                             List.of(newName),
                                             newVisibility,
                                             oldToNewParameters.values().stream().filter(p -> p != newReceiver).toList(),
                                             newReceiver,
                                             originalDescriptor.getTypeParameters(),
                                             newReplacementMap,
                                             originalDescriptor.getControlFlow(),
                                             returnType,
                                             originalDescriptor.getModifiers(),
                                             originalDescriptor.getOptInMarkers(),
                                             originalDescriptor.getRenderedAnnotations());
    }
}
