// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PositionTracker;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.references.ReferenceUtilsKt;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public abstract class AbstractKotlinInplaceVariableIntroducer<D extends KtCallableDeclaration, KotlinType> extends InplaceVariableIntroducer<KtExpression> {
    private static final Key<AbstractKotlinInplaceVariableIntroducer> ACTIVE_INTRODUCER = Key.create("ACTIVE_INTRODUCER");

    private static final Function0<Boolean> TRUE = new Function0<>() {
        @Override
        public Boolean invoke() {
            return true;
        }
    };

    private static final Consumer<? super JComponent> DO_NOTHING = __ -> {};

    protected static final class ControlWrapper {
        private final @NotNull Function0<JComponent> factory;
        private final @NotNull Function0<Boolean> condition;
        private final @NotNull Consumer<? super JComponent> initializer;
        private JComponent component;

        public ControlWrapper(
          @NotNull Function0<JComponent> factory,
          @NotNull Function0<Boolean> condition,
          @NotNull Consumer<? super JComponent> initializer) {
            this.factory = factory;
            this.condition = condition;
            this.initializer = initializer;
        }

        public ControlWrapper(@NotNull Function0<JComponent> factory) {
            this(factory, TRUE, DO_NOTHING);
        }

        public boolean isAvailable() {
            return condition.invoke();
        }

        public void initialize() {
            initializer.accept(getComponent());
        }

        public @NotNull JComponent getComponent() {
            if (component == null) {
                component = factory.invoke();
            }
            return component;
        }
    }

    private final boolean myReplaceOccurrence;
    protected D myDeclaration;
    private final boolean isVar;
    private final boolean myDoNotChangeVar;
    protected final @Nullable KotlinType myExprType;
    private final boolean noTypeInference;
    private final List<ControlWrapper> panelControls = new ArrayList<>();
    private JPanel contentPanel;

    public AbstractKotlinInplaceVariableIntroducer(
            PsiNamedElement elementToRename, Editor editor, Project project,
            @Nls String title, KtExpression[] occurrences,
            @Nullable KtExpression expr, boolean replaceOccurrence,
            D declaration, boolean isVar, boolean doNotChangeVar,
            @Nullable KotlinType exprType, boolean noTypeInference
    ) {
        super(elementToRename, editor, project, title, occurrences, expr);
        this.myReplaceOccurrence = replaceOccurrence;
        myDeclaration = declaration;
        this.isVar = isVar;
        myDoNotChangeVar = doNotChangeVar;
        myExprType = exprType;
        this.noTypeInference = noTypeInference;

        String advertisementActionId = getAdvertisementActionId();
        if (advertisementActionId != null) {
            showDialogAdvertisement(advertisementActionId);
        }
    }

    private boolean myRestart = false;
    protected void restart(RefactoringActionHandler handler) {
        myRestart = true;
        PsiFile file = myDeclaration.getContainingFile();
        TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
        if (state != null) {
            state.gotoEnd(true);
        }
        handler.invoke(myProject, myEditor, file, null);
    }

    protected @Nullable String getAdvertisementActionId() {
        return null;
    }

    private @NotNull JPanel getContentPanel() {
        if (contentPanel == null) {
            contentPanel = new JPanel(new GridBagLayout());
            contentPanel.setBorder(null);
        }

        return contentPanel;
    }

    protected final void addPanelControl(@NotNull ControlWrapper panelControl) {
        panelControls.add(panelControl);
    }

    protected final void addPanelControl(@Nullable Function0<JComponent> initializer) {
        if (initializer != null) {
            addPanelControl(new ControlWrapper(initializer));
        }
    }

    protected void initPanelControls() {
        addPanelControl(getCreateVarCheckBox());
        addPanelControl(getCreateExplicitTypeCheckBox());
    }

    protected final void updatePanelControls() {
        JPanel panel = getContentPanel();

        panel.removeAll();

        int count = 1;
        for (ControlWrapper panelControl : panelControls) {
            if (!panelControl.isAvailable()) continue;
            panelControl.initialize();
            panel.add(panelControl.getComponent(), new GridBagConstraints(0, count, 1, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                                          GridBagConstraints.HORIZONTAL,
                                                                          JBUI.insets(0, 5), 0, 0));
            ++count;
        }
        revalidate();
    }

    @Override
    protected final @NotNull JComponent getComponent() {
        panelControls.clear();
        initPanelControls();

        updatePanelControls();

        return getContentPanel();
    }

    protected abstract String renderType(KotlinType kotlinType);

    protected final @Nullable Function0<JComponent> getCreateExplicitTypeCheckBox() {
        if (myExprType == null || noTypeInference) return null;

        return new Function0<>() {
            @Override
            public JComponent invoke() {
                final JCheckBox exprTypeCheckbox = new NonFocusableCheckBox(
                        KotlinBundle.message("checkbox.text.specify.type.explicitly"));
                exprTypeCheckbox.setSelected(false);
                exprTypeCheckbox.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(@NotNull ActionEvent e) {
                        runWriteCommandAction(myProject, getCommandName(), getCommandName(),
                                              () -> {
                                                  if (exprTypeCheckbox.isSelected()) {
                                                      String renderedType = renderType(myExprType);
                                                      myDeclaration.setTypeReference(new KtPsiFactory(myProject).createType(renderedType));
                                                  } else {
                                                      myDeclaration.setTypeReference(null);
                                                  }
                                              }
                        );
                    }
                });

                return exprTypeCheckbox;
            }
        };
    }

    protected final @Nullable Function0<JComponent> getCreateVarCheckBox() {
        if (myDoNotChangeVar) return null;

        return new Function0<>() {
            @Override
            public JComponent invoke() {
                final JCheckBox varCheckbox = new NonFocusableCheckBox(KotlinBundle.message("checkbox.text.declare.with.var"));
                varCheckbox.setSelected(isVar);
                varCheckbox.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(@NotNull ActionEvent e) {
                        runWriteCommandAction(myProject, getCommandName(), getCommandName(), () -> {
                            PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());

                            KtPsiFactory psiFactory = new KtPsiFactory(myProject);
                            PsiElement keyword =
                                    varCheckbox.isSelected() ? psiFactory.createVarKeyword() : psiFactory.createValKeyword();

                            PsiElement valOrVar = myDeclaration instanceof KtProperty
                                                  ? ((KtProperty) myDeclaration).getValOrVarKeyword()
                                                  : ((KtParameter) myDeclaration).getValOrVarKeyword();
                            valOrVar.replace(keyword);
                        });
                    }
                });

                return varCheckbox;
            }
        };
    }

    protected final void runWriteActionAndRestartRefactoring(final Runnable runnable) {
        final Ref<Boolean> greedyToRight = new Ref<>();
        runWriteCommandAction(myProject, getCommandName(), getCommandName(), () -> {
            PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());

            ASTNode identifier = myDeclaration.getNode().findChildByType(KtTokens.IDENTIFIER);
            if (identifier != null) {
                TextRange range = identifier.getTextRange();
                RangeHighlighter[] highlighters = myEditor.getMarkupModel().getAllHighlighters();
                for (RangeHighlighter highlighter : highlighters) {
                    if (highlighter.getStartOffset() == range.getStartOffset()) {
                        if (highlighter.getEndOffset() == range.getEndOffset()) {
                            greedyToRight.set(highlighter.isGreedyToRight());
                            highlighter.setGreedyToRight(false);
                        }
                    }
                }
            }

            runnable.run();

            TemplateState templateState =
                    TemplateManagerImpl.getTemplateState(InjectedLanguageUtil.getTopLevelEditor(myEditor));
            if (templateState != null) {
                myEditor.putUserData(INTRODUCE_RESTART, true);
                templateState.gotoEnd(true);
            }
        });
        ApplicationManager.getApplication().runReadAction(() -> {
            ASTNode identifier = myDeclaration.getNode().findChildByType(KtTokens.IDENTIFIER);
            if (identifier != null) {
                TextRange range = identifier.getTextRange();
                RangeHighlighter[] highlighters = myEditor.getMarkupModel().getAllHighlighters();
                for (RangeHighlighter highlighter : highlighters) {
                    if (highlighter.getStartOffset() == range.getStartOffset()) {
                        if (highlighter.getEndOffset() == range.getEndOffset()) {
                            highlighter.setGreedyToRight(greedyToRight.get());
                        }
                    }
                }
            }
        });

        if (myEditor.getUserData(INTRODUCE_RESTART) == Boolean.TRUE) {
            myInitialName = myDeclaration.getName();
            performInplaceRefactoring(getSuggestionsForNextRun());
        }
    }

    private LinkedHashSet<String> getSuggestionsForNextRun() {
        LinkedHashSet<String> nameSuggestions;
        String currentName = myDeclaration.getName();
        if (myNameSuggestions.contains(currentName)) {
            nameSuggestions = myNameSuggestions;
        }
        else {
            nameSuggestions = new LinkedHashSet<>();
            nameSuggestions.add(currentName);
            nameSuggestions.addAll(myNameSuggestions);
        }
        return nameSuggestions;
    }

    protected void revalidate() {
        getContentPanel().revalidate();
        if (myTarget != null) {
            myBalloon.revalidate(new PositionTracker.Static<>(myTarget));
        }
    }

    protected abstract void addTypeReferenceVariable(TemplateBuilderImpl builder);
    protected abstract TemplateEditingListener createTypeReferencePostprocessor();

    @Override
    protected void addAdditionalVariables(TemplateBuilderImpl builder) {
        addTypeReferenceVariable(builder);
    }

    @Override
    protected boolean buildTemplateAndStart(
      @NotNull Collection<PsiReference> refs,
      @NotNull Collection<Pair<PsiElement, TextRange>> stringUsages,
      @NotNull PsiElement scope,
      @NotNull PsiFile containingFile
    ) {
        myEditor.putUserData(INTRODUCE_RESTART, false);
        //noinspection ConstantConditions
        myEditor.getCaretModel().moveToOffset(getNameIdentifier().getTextOffset());
        boolean result = super.buildTemplateAndStart(refs, stringUsages, scope, containingFile);

        TemplateState templateState =
                TemplateManagerImpl.getTemplateState(InjectedLanguageUtil.getTopLevelEditor(myEditor));
        if (templateState != null) {
            if (myDeclaration.getTypeReference() != null) {
                TemplateEditingListener postprocessor = createTypeReferencePostprocessor();
                templateState.addTemplateStateListener(new TemplateEditingAdapter() {
                    @Override
                    public void templateFinished(@NotNull Template template, boolean brokenOff) {
                        if (!myRestart) {
                            postprocessor.templateFinished(template, brokenOff);
                        }
                    }
                });
            }
            templateState.addTemplateStateListener(new TemplateEditingAdapter() {
                @Override
                public void templateFinished(@NotNull Template template, boolean brokenOff) {
                    if (brokenOff) {
                        onCancel(myRestart);
                    }
                }

                @Override
                public void templateCancelled(Template template) {
                    onCancel(myRestart);
                }
            });
        }

        return result;
    }

    protected void onCancel(boolean restart) {}

    @Override
    protected Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
        return CollectionsKt.map(
                ArraysKt.filterIsInstance(getOccurrences(), KtSimpleNameExpression.class),
                new Function1<>() {
                    @Override
                    public PsiReference invoke(KtSimpleNameExpression expression) {
                        return ReferenceUtilsKt.getMainReference(expression);
                    }
                }
        );
    }

    @Override
    public boolean performInplaceRefactoring(LinkedHashSet<String> nameSuggestions) {
        if (super.performInplaceRefactoring(nameSuggestions)) {
            myEditor.putUserData(ACTIVE_INTRODUCER, this);
            return true;
        }
        return false;
    }

    @Override
    public void finish(boolean success) {
        super.finish(success);
        myEditor.putUserData(ACTIVE_INTRODUCER, null);
    }

    @Override
    protected void moveOffsetAfter(boolean success) {
        if (!myReplaceOccurrence || myExprMarker == null) {
            myEditor.getCaretModel().moveToOffset(myDeclaration.getTextRange().getEndOffset());
        }
        else {
            int startOffset = myExprMarker.getStartOffset();
            PsiFile file = myDeclaration.getContainingFile();
            PsiElement elementAt = file.findElementAt(startOffset);
            if (elementAt != null) {
                myEditor.getCaretModel().moveToOffset(elementAt.getTextRange().getEndOffset());
            }
            else {
                myEditor.getCaretModel().moveToOffset(myExprMarker.getEndOffset());
            }
        }
    }

    public void stopIntroduce() {
        final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
        if (templateState != null) {
            Runnable runnable = () -> templateState.gotoEnd(true);
            CommandProcessor.getInstance().executeCommand(myProject, runnable, getCommandName(), getCommandName());
        }
    }

    public static @Nullable AbstractKotlinInplaceVariableIntroducer getActiveInstance(@NotNull Editor editor) {
        return editor.getUserData(ACTIVE_INTRODUCER);
    }
}
