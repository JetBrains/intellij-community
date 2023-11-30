// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages.dialogs;

import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.FindMethodUsagesDialog;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.asJava.elements.KtLightMethod;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions;
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.psi.psiUtil.PsiUtilsKt;

import javax.swing.*;

import static org.jetbrains.kotlin.idea.base.searching.usages.dialogs.Utils.isAbstract;
import static org.jetbrains.kotlin.idea.base.searching.usages.dialogs.Utils.isOpen;

public class KotlinFindFunctionUsagesDialog extends FindMethodUsagesDialog {
    private StateRestoringCheckBox expectedUsages;
    private StateRestoringCheckBox overrideUsages;

    public KotlinFindFunctionUsagesDialog(
            PsiMethod method,
            Project project,
            KotlinFunctionFindUsagesOptions findUsagesOptions,
            boolean toShowInNewTab,
            boolean mustOpenInNewTab,
            boolean isSingleFile,
            FindUsagesHandler handler
    ) {
        super(method, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
    }

    @Override
    public boolean isIncludeOverloadedMethodsAvailable() {
        return true;
    }

    @NotNull
    @Override
    protected KotlinFunctionFindUsagesOptions getFindUsagesOptions() {
        return (KotlinFunctionFindUsagesOptions) myFindUsagesOptions;
    }

    @Override
    public void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent) {
        coloredComponent.append(KotlinFindUsagesSupport.Companion.formatJavaOrLightMethod((PsiMethod) myPsiElement));
    }

    @Override
    protected JPanel createFindWhatPanel() {
        JPanel findWhatPanel = super.createFindWhatPanel();

        if (findWhatPanel != null) {
            Utils.removeCheckbox(
                    findWhatPanel,
                    JavaBundle.message("find.what.implementing.methods.checkbox")
            );
            Utils.removeCheckbox(
                    findWhatPanel,
                    JavaBundle.message("find.what.overriding.methods.checkbox")
            );
        }

        return findWhatPanel;
    }

    @Override
    protected void addUsagesOptions(JPanel optionsPanel) {
        super.addUsagesOptions(optionsPanel);

        KtNamedDeclaration method = (KtNamedDeclaration) myUsagesHandler.getPsiElement();
        if (isOpen(method)) {
            overrideUsages = addCheckboxToPanel(
              isAbstract(method)
              ? KotlinBundle.message("find.declaration.implementing.methods.checkbox")
              : KotlinBundle.message("find.declaration.overriding.methods.checkbox"),
              getFindUsagesOptions().getSearchOverrides(),
              optionsPanel,
              true
            );
        }

        if (!Utils.renameCheckbox(
                optionsPanel,
                JavaBundle.message("find.options.include.overloaded.methods.checkbox"),
                KotlinBundle.message("find.declaration.include.overloaded.methods.checkbox")
        )) {
            addCheckboxToPanel(
                    KotlinBundle.message("find.declaration.include.overloaded.methods.checkbox"),
                    FindSettings.getInstance().isSearchOverloadedMethods(),
                    optionsPanel,
                    false
            );
        }
        PsiElement element = LightClassUtilsKt.getUnwrapped(getPsiElement());
        //noinspection ConstantConditions
        KtDeclaration function = element instanceof KtNamedDeclaration
                                 ? (KtNamedDeclaration) element
                                 : ((KtLightMethod) element).getKotlinOrigin();

        boolean isActual = function != null && PsiUtilsKt.hasActualModifier(function);
        KotlinFunctionFindUsagesOptions options = getFindUsagesOptions();
        if (isActual) {
            expectedUsages = addCheckboxToPanel(
                    KotlinBundle.message("find.usages.checkbox.name.expected.functions"),
                    options.getSearchExpected(),
                    optionsPanel,
                    false
            );
        }
    }

    @Override
    public void calcFindUsagesOptions(JavaMethodFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);

        options.isOverridingMethods = isSelected(overrideUsages);

        KotlinFunctionFindUsagesOptions kotlinOptions = (KotlinFunctionFindUsagesOptions) options;
        if (expectedUsages != null) {
            kotlinOptions.setSearchExpected(expectedUsages.isSelected());
        }
    }

    @Override
    protected void update() {
        super.update();
        if (!isOKActionEnabled() && (isSelected(overrideUsages))) {
            setOKActionEnabled(true);
        }
    }

}
