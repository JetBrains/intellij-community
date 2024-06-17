// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.NonFocusableCheckBox
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeChooseValueExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.createPostTypeUpdateProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty.KotlinIntroducePropertyHandler.InteractiveExtractionHelperWithOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.TYPE_REFERENCE_VARIABLE_NAME
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionTarget
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicatesSilently
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.AbstractKotlinInplaceVariableIntroducer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.awt.event.ItemEvent
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

open class KotlinInplacePropertyIntroducer(
    property: KtProperty,
    editor: Editor,
    project: Project,
    @Nls title: String,
    doNotChangeVar: Boolean,
    exprType: TypeInfo?,
    private var extractionResult: ExtractionResult,
    private val availableTargets: List<ExtractionTarget>,
    replaceAllByDefault: Boolean = true
) : AbstractKotlinInplaceVariableIntroducer<KtProperty, TypeInfo>(
    property, editor, project, title, KtExpression.EMPTY_ARRAY, null, false, property, false, doNotChangeVar, exprType, false
) {
    init {
        assert(availableTargets.isNotEmpty()) { "No targets available: ${property.getElementTextWithContext()}" }
    }

    private var currentTarget: ExtractionTarget = extractionResult.config.generatorOptions.target
        set(value) {
            if (value == currentTarget) return

            field = value
            restart(KotlinIntroducePropertyHandler(InteractiveExtractionHelperWithOptions(value)))
        }

    private var replaceAll: Boolean = replaceAllByDefault

    private var property: KtProperty
        get() = myDeclaration
        set(value) {
            myDeclaration = value
        }

    private fun isInitializer(): Boolean = currentTarget == ExtractionTarget.PROPERTY_WITH_INITIALIZER

    override fun initPanelControls() {
        if (availableTargets.size > 1) {
            addPanelControl(
                ControlWrapper {
                    val propertyKindComboBox = with(JComboBox(availableTargets.map { it.targetName.capitalize() }.toTypedArray())) {
                        addItemListener { itemEvent ->
                            if (itemEvent.stateChange == ItemEvent.SELECTED) {
                                ApplicationManager.getApplication().invokeLater {
                                    currentTarget = availableTargets[selectedIndex]
                                }
                            }
                        }

                        selectedIndex = availableTargets.indexOf(currentTarget)

                        this
                    }

                    val propertyKindLabel = JLabel(KotlinBundle.message("label.text.introduce.as"))
                    propertyKindLabel.labelFor = propertyKindComboBox

                    val panel = JPanel()
                    panel.add(propertyKindLabel)
                    panel.add(propertyKindComboBox)

                    panel
                }
            )
        }

        if (ExtractionTarget.PROPERTY_WITH_INITIALIZER in availableTargets) {
            val condition = { isInitializer() }

            createVarCheckBox?.let {
                addPanelControl(ControlWrapper(it, condition
                ) {
                    (it as JCheckBox).isSelected = property.isVar
                })
            }
            createExplicitTypeCheckBox?.let {
                addPanelControl(ControlWrapper(it, condition
                ) {
                    (it as JCheckBox).isSelected = property.typeReference != null
                })
            }
        }

        val occurrenceCount = extractionResult.duplicateReplacers.size
        if (occurrenceCount > 1) {
            addPanelControl(
                ControlWrapper {
                    val replaceAllCheckBox = NonFocusableCheckBox(
                        KotlinBundle.message("checkbox.text.replace.all.occurrences.0", occurrenceCount))
                    replaceAllCheckBox.isSelected = replaceAll
                    replaceAllCheckBox.addActionListener { replaceAll = replaceAllCheckBox.isSelected }
                    replaceAllCheckBox
                }
            )
        }
    }

    override fun addTypeReferenceVariable(builder: TemplateBuilderImpl) {
        if (!isInitializer()) return
        val typeReference = myDeclaration.getTypeReference();
        val exprType = myExprType
        if (exprType != null && typeReference != null) {
            val expression = TypeChooseValueExpression(listOf(exprType.defaultType) + exprType.otherTypes, exprType.defaultType)
            builder.replaceElement(typeReference, TYPE_REFERENCE_VARIABLE_NAME, expression, false);
        }
    }

    override fun renderType(kotlinType: TypeInfo): String = kotlinType.defaultType.shortTypeRepresentation

    override fun createTypeReferencePostprocessor(): TemplateEditingListener {
        return createPostTypeUpdateProcessor(myDeclaration, listOf(myDeclaration to myExprType!!).iterator(), myProject, myEditor)
    }

    override fun checkLocalScope(): PsiElement {
        return myElementToRename.parentsWithSelf.first { it is KtClassOrObject || it is KtFile }
    }

    public override fun performRefactoring(): Boolean {
        if (replaceAll) {
            processDuplicatesSilently(extractionResult.duplicateReplacers, myProject)
        }
        else {
            val entry = extractionResult.duplicateReplacers.entries.first()
            processDuplicatesSilently(mapOf(entry.key to entry.value), myProject)
        }
        return true
    }
}