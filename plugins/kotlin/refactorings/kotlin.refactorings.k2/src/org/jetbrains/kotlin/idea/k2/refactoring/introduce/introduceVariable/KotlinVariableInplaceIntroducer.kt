// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil.SelectableTemplateElement
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil.createNavigatableButtonWithPopup
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil.createSettingsPresentation
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.application
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractKotlinInplaceIntroducer
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import javax.swing.JCheckBox
import javax.swing.JPanel
import kotlin.reflect.KFunction2

class KotlinVariableInplaceIntroducer(
    addedVariable: KtProperty,
    originalExpression: KtExpression?,
    occurrencesToReplace: Array<KtExpression>,
    private val suggestedNames: Collection<String>,
    private val expressionRenderedType: String?,
    private val mustSpecifyTypeExplicitly: Boolean,
    @Nls title: String,
    project: Project,
    editor: Editor,
    private val postProcess: KFunction2<KtDeclaration, Editor?, Unit>,
) : AbstractKotlinInplaceIntroducer<KtProperty>(
    localVariable = addedVariable.takeIf { it.isLocal },
    expression = originalExpression,
    occurrences = occurrencesToReplace,
    title = title,
    project = project,
    editor = editor,
) {
    private var expressionTypeCheckBox: JCheckBox? = null
    private val addedVariablePointer: SmartPsiElementPointer<KtProperty> = addedVariable.createSmartPointer()
    private val addedVariable: KtProperty?
        get() = addedVariablePointer.element

    private fun createPopupPanel(): DialogPanel {
        return panel {
            row {
                checkBox(KotlinBundle.message("checkbox.text.declare.with.var"))
                    .selected(KotlinCommonRefactoringSettings.getInstance().INTRODUCE_DECLARE_WITH_VAR)
                    .actionListener { _, component ->
                        myProject.executeWriteCommand(commandName, commandName) {
                            val psiFactory = KtPsiFactory(myProject)
                            val keyword = if (component.isSelected) psiFactory.createVarKeyword() else psiFactory.createValKeyword()
                            addedVariable?.valOrVarKeyword?.replace(keyword)
                            KotlinCommonRefactoringSettings.getInstance().INTRODUCE_DECLARE_WITH_VAR = component.isSelected
                        }
                    }
            }
            if (expressionRenderedType != null && !mustSpecifyTypeExplicitly) {
                row {
                    checkBox(KotlinBundle.message("specify.type.explicitly")).apply {
                        selected(KotlinCommonRefactoringSettings.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY)
                        actionListener { _, component ->
                            runWriteCommandAndRestart {
                                updateVariableName()
                                if (component.isSelected) {
                                    addedVariable?.typeReference = KtPsiFactory(myProject).createType(expressionRenderedType)
                                    shortenReferences(addedVariable!!)
                                } else {
                                    addedVariable?.typeReference = null
                                }
                                KotlinCommonRefactoringSettings.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY = component.isSelected
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getVariable(): KtProperty? = addedVariable

    override fun suggestNames(replaceAll: Boolean, variable: KtProperty?): Array<String> {
        return suggestedNames.toTypedArray()
    }

    override fun createFieldToStartTemplateOn(replaceAll: Boolean, names: Array<out String>): KtProperty? = addedVariable

    override fun buildTemplateAndStart(
        refs: Collection<PsiReference>,
        stringUsages: Collection<Pair<PsiElement, TextRange>>,
        scope: PsiElement,
        containingFile: PsiFile
    ): Boolean {
        myNameSuggestions = myNameSuggestions.mapTo(LinkedHashSet(), String::quoteIfNeeded)

        myEditor.caretModel.moveToOffset(nameIdentifier!!.startOffset)

        val result = super.buildTemplateAndStart(refs, stringUsages, scope, containingFile)
        val variable = addedVariable
        val templateState = TemplateManagerImpl.getTemplateState(InjectedLanguageEditorUtil.getTopLevelEditor(myEditor))
        if (templateState != null && variable?.typeReference != null) {
            templateState.addTemplateStateListener(createTypeReferencePostprocessor(variable))
        }

        return result
    }

    private fun createTypeReferencePostprocessor(
        variable: KtCallableDeclaration,
    ): TemplateEditingAdapter = object : TemplateEditingAdapter() {
        override fun templateFinished(template: Template, brokenOff: Boolean) {
            val context = analyzeInModalWindow(variable, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
                CallableReturnTypeUpdaterUtils.getTypeInfo(variable)
            }
            application.runWriteAction {
                CallableReturnTypeUpdaterUtils.updateType(variable, context, myProject, myEditor)
            }
        }
    }

    override fun afterTemplateStart() {
        super.afterTemplateStart()
        val templateState = TemplateManagerImpl.getTemplateState(myEditor) ?: return

        val currentVariableRange = templateState.currentVariableRange ?: return

        val popupComponent = createPopupPanel()

        val presentation = createSettingsPresentation(templateState.editor as EditorImpl) {}
        val templateElement: SelectableTemplateElement = object : SelectableTemplateElement(presentation) {}

        createNavigatableButtonWithPopup(
            templateState,
            currentVariableRange.endOffset,
            presentation,
            popupComponent as JPanel,
            templateElement,
            isPopupAbove = true
        ) { }
    }

    override fun getInitialName(): String = super.getInitialName().quoteIfNeeded()

    override fun updateTitle(variable: KtProperty?, value: String?) {
        expressionTypeCheckBox?.isEnabled = value == null || value.isIdentifier()
        // No preview to update
    }

    override fun deleteTemplateField(psiField: KtProperty?) {
        // Do not delete introduced variable as it was created outside of in-place refactoring
    }

    override fun isReplaceAllOccurrences(): Boolean = true

    override fun setReplaceAllOccurrences(allOccurrences: Boolean) {

    }

    override fun getComponent(): JPanel? = myWholePanel

    override fun performIntroduce() {
        val newName = inputName ?: return
        val replacement = KtPsiFactory(myProject).createExpression(newName)

        application.runWriteAction {
            addedVariable?.setName(newName)
            occurrences.forEach {
                if (it.isValid) {
                    it.replace(replacement)
                }
            }
        }
    }

    override fun moveOffsetAfter(success: Boolean) {
        super.moveOffsetAfter(success)
        if (success) {
            addedVariable?.let { postProcess(it, myEditor) }
        }
    }
}
