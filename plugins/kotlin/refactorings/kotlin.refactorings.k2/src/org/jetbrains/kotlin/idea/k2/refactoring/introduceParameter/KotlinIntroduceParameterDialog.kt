// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.NonFocusableCheckBox
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.Generator
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionTarget
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicates
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.INTRODUCE_PARAMETER
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.IntroduceParameterDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceParameterHelper
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.*
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class KotlinIntroduceParameterDialog(
    project: Project,
    val editor: Editor,
    val descriptor: IntroduceParameterDescriptor<KtNamedDeclaration>,
    nameSuggestions: Array<String>,
    typeNameSuggestions: List<String>,
    val helper: KotlinIntroduceParameterHelper<KtNamedDeclaration>,
    private val lambdaExtractionDescriptor: ExtractableCodeDescriptor? = null
) : RefactoringDialog(project, true) {

    private val nameField = NameSuggestionsField(nameSuggestions, project, KotlinFileType.INSTANCE)
    private val typeField = NameSuggestionsField(typeNameSuggestions.toTypedArray(), project, KotlinFileType.INSTANCE)
    private var replaceAllCheckBox: JCheckBox? = null
    private var defaultValueCheckBox: JCheckBox? = null
    private val removeParamsCheckBoxes = LinkedHashMap<JCheckBox, KtElement>(descriptor.parametersToRemove.size)

    @Nls
    private val commandName = INTRODUCE_PARAMETER

    init {
        title = commandName
        init()

        nameField.addDataChangedListener { validateButtons() }
        typeField.addDataChangedListener { validateButtons() }
        if (lambdaExtractionDescriptor != null) {
            typeField.isEnabled = false
        }
    }

    override fun getPreferredFocusedComponent() = nameField.focusableComponent

    private fun updateRemoveParamCheckBoxes() {
        val enableParamRemove = (replaceAllCheckBox?.isSelected ?: true) && (!defaultValueCheckBox!!.isSelected)
        removeParamsCheckBoxes.keys.forEach {
            it.isEnabled = enableParamRemove
            it.isSelected = enableParamRemove
        }
    }

    override fun createNorthPanel(): JComponent {
        val gbConstraints = GridBagConstraints()

        val panel = JPanel(GridBagLayout())

        gbConstraints.anchor = GridBagConstraints.WEST
        gbConstraints.fill = GridBagConstraints.NONE
        gbConstraints.gridx = 0

        gbConstraints.insets = Insets(4, 4, 4, 0)
        gbConstraints.gridwidth = 1
        gbConstraints.weightx = 0.0
        gbConstraints.weighty = 0.0
        gbConstraints.gridy = 0
        val nameLabel = JLabel(KotlinBundle.message("text.parameter.name"))
        nameLabel.labelFor = nameField
        panel.add(nameLabel, gbConstraints)

        gbConstraints.insets = Insets(4, 4, 4, 8)
        gbConstraints.gridx++
        gbConstraints.weightx = 1.0
        gbConstraints.fill = GridBagConstraints.BOTH
        panel.add(nameField, gbConstraints)

        gbConstraints.insets = Insets(4, 4, 4, 8)
        gbConstraints.gridwidth = 1
        gbConstraints.weightx = 0.0
        gbConstraints.gridx = 0
        gbConstraints.gridy++
        gbConstraints.fill = GridBagConstraints.NONE
        val typeLabel = JLabel(KotlinBundle.message("text.parameter.type"))
        typeLabel.labelFor = typeField
        panel.add(typeLabel, gbConstraints)

        gbConstraints.gridx++
        gbConstraints.insets = Insets(4, 4, 4, 8)
        gbConstraints.weightx = 1.0
        gbConstraints.fill = GridBagConstraints.BOTH
        panel.add(typeField, gbConstraints)

        gbConstraints.fill = GridBagConstraints.HORIZONTAL
        gbConstraints.gridx = 0
        gbConstraints.insets = Insets(4, 0, 4, 8)
        gbConstraints.gridwidth = 2
        gbConstraints.gridy++

        val defaultValueCheckBox = NonFocusableCheckBox(KotlinBundle.message("text.introduce.default.value"))
        defaultValueCheckBox.isSelected = descriptor.withDefaultValue
        defaultValueCheckBox.addActionListener { updateRemoveParamCheckBoxes() }
        panel.add(defaultValueCheckBox, gbConstraints)

        this.defaultValueCheckBox = defaultValueCheckBox

        val occurrenceCount = descriptor.occurrencesToReplace.size

        if (occurrenceCount > 1) {
            gbConstraints.gridy++
            val replaceAllCheckBox = NonFocusableCheckBox(
                KotlinBundle.message("checkbox.text.replace.all.occurrences.0", occurrenceCount)
            )
            replaceAllCheckBox.isSelected = true
            replaceAllCheckBox.addActionListener { updateRemoveParamCheckBoxes() }
            panel.add(replaceAllCheckBox, gbConstraints)
            this.replaceAllCheckBox = replaceAllCheckBox
        }

        if (replaceAllCheckBox != null) {
            gbConstraints.insets = Insets(0, 16, 4, 8)
        }

        for (parameter in descriptor.parametersToRemove) {
            val removeWhat = if (parameter is KtParameter)
                KotlinBundle.message("text.parameter.0", parameter.name.toString())
            else
                KotlinBundle.message("text.receiver")
            val cb = NonFocusableCheckBox(
                KotlinBundle.message("text.remove.0.no.longer.used", removeWhat)
            )

            removeParamsCheckBoxes[cb] = parameter
            cb.isSelected = true
            gbConstraints.gridy++
            panel.add(cb, gbConstraints)
        }

        return panel
    }

    override fun createCenterPanel() = null

    override fun canRun() {
        if (!nameField.enteredName.quoteIfNeeded().isIdentifier()) {
            throw ConfigurationException(KotlinBundle.message("error.text.invalid.parameter.name"))
        }

        if (KtPsiFactory(myProject).createTypeIfPossible(typeField.enteredName) == null) {
            throw ConfigurationException(KotlinBundle.message("error.text.invalid.parameter.type"))
        }
    }

    override fun doAction() {
        val startMarkAction = performRefactoring()
        FinishMarkAction.finish(myProject, editor, startMarkAction)
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    fun performRefactoring(): StartMarkAction {
        close(OK_EXIT_CODE)

        return project.executeCommand(commandName) {
            fun createLambdaForArgument(function: KtFunction): KtExpression {
                val statement = function.bodyBlockExpression!!.statements.single()
                val space = if (statement.isMultiLine()) "\n" else " "
                val parameters = function.valueParameters
                val parametersText = if (parameters.isNotEmpty()) {
                    " " + parameters.asSequence().map { it.name }.joinToString() + " ->"
                } else ""
                val text = "{$parametersText$space${statement.text}$space}"

                return KtPsiFactory(myProject).createExpression(text)
            }

            val chosenName = nameField.enteredName.quoteIfNeeded()
            var chosenType = typeField.enteredName
            var newArgumentValue = descriptor.newArgumentValue
            var newReplacer = descriptor.occurrenceReplacer

            val startMarkAction = StartMarkAction.start(editor, myProject, this@KotlinIntroduceParameterDialog.commandName)

            if (lambdaExtractionDescriptor != null) {
                val options = ExtractionGeneratorOptions.DEFAULT.copy(
                    target = ExtractionTarget.FAKE_LAMBDALIKE_FUNCTION,
                    allowExpressionBody = false
                )

                var extractionResult: ExtractionResult? = null
                if (!ApplicationManagerEx.getApplicationEx()
                        .runWriteActionWithCancellableProgressInDispatchThread(KotlinBundle.message("perform.refactoring"), project, null) {
                            allowAnalysisOnEdt {
                                allowAnalysisFromWriteAction {
                                    runWriteAction {
                                        extractionResult = Generator.generateDeclaration(
                                            ExtractionGeneratorConfiguration(
                                                lambdaExtractionDescriptor.copy(suggestedNames = listOf(chosenName)),
                                                options
                                            ), null
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    return@executeCommand startMarkAction
                }

                if (extractionResult == null) {
                    return@executeCommand startMarkAction
                }
                val (_, declaration, duplicateReplacers) = extractionResult!!

                with(declaration) {
                    val function = declaration as KtFunction
                    val receiverType = function.receiverTypeReference?.text
                    val parameterTypes = function
                        .valueParameters.joinToString { it.typeReference!!.text }
                    val returnType = function.typeReference?.text ?: "Unit"

                    chosenType = (receiverType?.let { "$it." } ?: "") + "($parameterTypes) -> $returnType"
                    if (KtTokens.SUSPEND_KEYWORD in lambdaExtractionDescriptor.modifiers) {
                        chosenType = "${KtTokens.SUSPEND_KEYWORD} $chosenType"
                    }
                    newArgumentValue = createLambdaForArgument(function)
                    newReplacer = { }

                }
                processDuplicates(duplicateReplacers, myProject, editor)
            }

            val descriptorToRefactor = descriptor.copy(
                newParameterName = chosenName,
                newParameterTypeText = chosenType,
                argumentValue = newArgumentValue,
                withDefaultValue = defaultValueCheckBox!!.isSelected,
                occurrencesToReplace = with(descriptor) {
                    if (replaceAllCheckBox?.isSelected != false) {
                        occurrencesToReplace
                    } else {
                        Collections.singletonList(originalOccurrence)
                    }
                },
                parametersToRemove = removeParamsCheckBoxes.filter { it.key.isEnabled && it.key.isSelected }.map { it.value },
                occurrenceReplacer = newReplacer
            )

            val configure: IntroduceParameterDescriptor<KtNamedDeclaration> = helper.configure(descriptorToRefactor)
            configure.performRefactoring(editor)
            startMarkAction
        }
    }

    override fun hasHelpAction(): Boolean = false
}
