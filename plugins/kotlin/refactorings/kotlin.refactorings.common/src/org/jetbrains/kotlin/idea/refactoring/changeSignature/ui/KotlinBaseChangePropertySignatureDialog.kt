// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.MethodSignatureComponent
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.Alarm
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableParameterInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.KotlinSignatureComponent
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeCodeFragment
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

abstract class KotlinBaseChangePropertySignatureDialog<P: KotlinModifiableParameterInfo, V, M : KotlinModifiableMethodDescriptor<P, V>>(
    project: Project,
    private val methodDescriptor: M
) : RefactoringDialog(project, true) {
    protected val visibilityCombo = ComboBox<V>()

    private val signatureUpdater = object : DocumentListener, ChangeListener, ActionListener {
        private fun update() = updateSignature()

        override fun documentChanged(event: DocumentEvent) = update()
        override fun stateChanged(e: ChangeEvent?) = update()
        override fun actionPerformed(e: ActionEvent?) = update()
    }

    protected val kotlinPsiFactory = KtPsiFactory(project)

    protected val returnTypeCodeFragment = createReturnTypeCodeFragment(methodDescriptor)

    protected val receiverTypeCodeFragment = createReceiverTypeCodeFragment(methodDescriptor)

    protected val receiverDefaultValueCodeFragment = kotlinPsiFactory.createExpressionCodeFragment(
        "",
        methodDescriptor.baseDeclaration,
    )

    private val nameField = EditorTextField(methodDescriptor.name).apply { addDocumentListener(signatureUpdater) }
    protected val name: String get() = nameField.text.quoteIfNeeded()

    private val returnTypeField = createKotlinEditorTextField(returnTypeCodeFragment, withListener = true)
    private val receiverTypeField = createKotlinEditorTextField(receiverTypeCodeFragment, withListener = true)
    private val receiverDefaultValueField = createKotlinEditorTextField(receiverDefaultValueCodeFragment, withListener = false)

    protected var receiverTypeCheckBox: JCheckBox? = null

    private val updateSignatureAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, myDisposable)
    private val signatureComponent: MethodSignatureComponent = KotlinSignatureComponent("", project).apply {
        preferredSize = Dimension(-1, 130)
        minimumSize = Dimension(-1, 130)
    }

    init {
        title = RefactoringBundle.message("changeSignature.refactoring.name")
        fillVisibilities(visibilityCombo.model as DefaultComboBoxModel<V>)
        init()
    }

    protected abstract fun fillVisibilities(model: DefaultComboBoxModel<V>)

    protected abstract fun createReturnTypeCodeFragment(m: M): KtTypeCodeFragment
    protected abstract fun createReceiverTypeCodeFragment(m: M): KtTypeCodeFragment

    private fun updateSignature(): Unit = SwingUtilities.invokeLater label@{
        if (Disposer.isDisposed(myDisposable)) return@label

        updateSignatureAlarm.cancelAllRequests()
        updateSignatureAlarm.addRequest(
            { PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted { updateSignatureAlarmFired() } },
            100,
            ModalityState.stateForComponent(signatureComponent)
        )
    }

    private fun updateSignatureAlarmFired() {
        doUpdateSignature()
        validateButtons()
    }

    private fun doUpdateSignature() {
        signatureComponent.setSignature(calculateSignature())
    }

    protected abstract fun isDefaultVisibility(v: V): Boolean

    private fun calculateSignature(): String = buildString {
        methodDescriptor.baseDeclaration.safeAs<KtValVarKeywordOwner>()?.valOrVarKeyword?.let { keyword ->
            val visibility = if (methodDescriptor.canChangeVisibility()) visibilityCombo.selectedItem as V else methodDescriptor.visibility
            if (visibility != null && !isDefaultVisibility(visibility)) {
                append(visibility).append(" ")
            }

            append(keyword.text).append(" ")
        }

        if (receiverTypeCheckBox?.isSelected == true && receiverTypeField.isEnabled) {
            val receiverText = receiverTypeField.text
            if (receiverText.contains("->")) {
                append("($receiverText).")
            } else {
                append(receiverText).append(".")
            }
        }

        append("$name: ${returnTypeField.text}")
    }

    override fun getPreferredFocusedComponent() = nameField

    override fun createCenterPanel(): JComponent = panel {
        if (methodDescriptor.canChangeVisibility()) {
            row(KotlinBundle.message("label.text.visibility")) {
                cell(visibilityCombo)
                    .applyToComponent {
                        selectedItem = methodDescriptor.visibility
                        addActionListener(signatureUpdater)
                    }
            }
        }

        row(KotlinBundle.message("label.text.name")) { cell(nameField).align(AlignX.FILL) }
        row(KotlinBundle.message("label.text.type")) { cell(returnTypeField).align(AlignX.FILL) }

        if (methodDescriptor.baseDeclaration is KtProperty) {
            row {
                receiverTypeCheckBox = checkBox(KotlinBundle.message("checkbox.text.extension.property"))
                    .applyToComponent {
                        addActionListener(signatureUpdater)
                        isSelected = methodDescriptor.receiver != null
                    }.component
            }

            row(KotlinBundle.message("label.text.receiver.type")) {
                cell(receiverTypeField).align(AlignX.FILL)
            }.enabledIf(receiverTypeCheckBox!!.selected)

            if (methodDescriptor.receiver == null) {
                row(KotlinBundle.message("label.text.default.receiver.value")) {
                    cell(receiverDefaultValueField).align(AlignX.FILL)
                }.enabledIf(receiverTypeCheckBox!!.selected)
            }
        }

        group(RefactoringBundle.message("signature.preview.border.title"), indent = false) {
            row { cell(signatureComponent).align(AlignX.FILL) }
        }

        updateSignature()
    }

    protected fun createKotlinEditorTextField(file: PsiFile, withListener: Boolean): EditorTextField = EditorTextField(
        PsiDocumentManager.getInstance(myProject).getDocument(file),
        myProject,
        KotlinFileType.INSTANCE
    ).apply {
        if (withListener) addDocumentListener(signatureUpdater)
    }

    override fun canRun() {
        if (!name.isIdentifier()) {
            throw ConfigurationException(KotlinBundle.message("error.text.invalid.name"))
        }

        if (!returnTypeCodeFragment.isValidType()) {
            throw ConfigurationException(KotlinBundle.message("error.text.invalid.return.type"))
        }
        if (receiverTypeCheckBox?.isSelected == true && !receiverTypeCodeFragment.isValidType()) {
            throw ConfigurationException(KotlinBundle.message("error.text.invalid.receiver.type"))
        }
    }


    protected abstract fun PsiCodeFragment?.isValidType(): Boolean


    override fun getHelpId(): String = "refactoring.changeSignature"
}