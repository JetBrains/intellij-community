// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.searching.usages.dialogs

import com.intellij.find.FindUsagesSettings
import com.intellij.find.findUsages.FindMethodUsagesDialog
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.StateRestoringCheckBox
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle.message
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import javax.swing.JPanel

class KotlinFindFunctionUsagesDialog(
    method: KtFunction,
    project: Project,
    findUsagesOptions: KotlinFunctionFindUsagesOptions,
    toShowInNewTab: Boolean,
    mustOpenInNewTab: Boolean,
    isSingleFile: Boolean,
    handler: FindUsagesHandler
) : FindMethodUsagesDialog(method, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler) {
    private var expectedUsages: StateRestoringCheckBox? = null
    private var overrideUsages: StateRestoringCheckBox? = null

    override fun noSpecificOptions(element: PsiElement): Boolean {
        return false
    }

    override fun canSearchForOverridingMethods(element: PsiElement): Boolean {
        return false
    }

    override fun canSearchForImplementingMethods(element: PsiElement): Boolean {
        return false
    }

    override fun canSearchForBaseMethod(element: PsiElement): Boolean {
        return element is KtFunction && !element.hasModifier(KtTokens.PRIVATE_KEYWORD)
    }

    override fun isIncludeOverloadedMethodsAvailable(): Boolean {
        return true
    }

    override fun getFindUsagesOptions(): KotlinFunctionFindUsagesOptions {
        return myFindUsagesOptions as KotlinFunctionFindUsagesOptions
    }

    override fun addUsagesOptions(optionsPanel: JPanel) {
        val method: KtNamedDeclaration = myUsagesHandler.psiElement as KtNamedDeclaration
        if (Utils.isOpen(method)) {
            overrideUsages = addCheckboxToPanel(
                if (Utils.isAbstract(method)
                ) message("find.declaration.implementing.methods.checkbox")
                else message("find.declaration.overriding.methods.checkbox"),
                getFindUsagesOptions().searchOverrides,
                optionsPanel,
                true
            )
        }
        myCbIncludeOverloadedMethods = addCheckboxToPanel(
            message("find.declaration.include.overloaded.methods.checkbox"),
            FindUsagesSettings.getInstance().isSearchOverloadedMethods(),
            optionsPanel,
            false
        )
        val element = psiElement.unwrapped
        val function = if (element is KtNamedDeclaration
        ) element as KtNamedDeclaration?
        else (element as KtLightMethod).kotlinOrigin

        val isActual = function != null && function.hasActualModifier()
        val options = findUsagesOptions
        if (isActual) {
            expectedUsages = addCheckboxToPanel(
                message("find.usages.checkbox.name.expected.functions"),
                options.searchExpected,
                optionsPanel,
                false
            )
        }
        addDefaultOptions(optionsPanel)
    }

    override fun calcFindUsagesOptions(options: JavaMethodFindUsagesOptions) {
        super.calcFindUsagesOptions(options)

        options.isOverridingMethods = isSelected(overrideUsages)

        val kotlinOptions: KotlinFunctionFindUsagesOptions = options as KotlinFunctionFindUsagesOptions
        if (expectedUsages != null) {
            kotlinOptions.searchExpected = expectedUsages!!.isSelected
        }
    }

    override fun update() {
        super.update()
        if (!isOKActionEnabled && isSelected(overrideUsages)) {
            isOKActionEnabled = true
        }
    }


    override fun configureLabelComponent(coloredComponent: SimpleColoredComponent) {
        coloredComponent.append(KotlinFindUsagesSupport.renderDeclaration(psiElement as KtFunction));
    }

}
