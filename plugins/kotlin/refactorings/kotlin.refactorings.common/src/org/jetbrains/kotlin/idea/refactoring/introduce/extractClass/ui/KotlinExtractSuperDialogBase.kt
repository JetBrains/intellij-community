// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.MemberInfoChange
import com.intellij.refactoring.extractSuperclass.JavaExtractSuperBaseDialog
import com.intellij.refactoring.util.DocCommentPolicy
import com.intellij.refactoring.util.RefactoringMessageUtil
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.onTextChange
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ExtractSuperInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinUsesAndInterfacesDependencyMemberInfoModel
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

@ApiStatus.Internal
abstract class KotlinExtractSuperDialogBase(
    protected val originalClass: KtClassOrObject,
    protected val targetParent: PsiElement,
    private val conflictChecker: (KotlinExtractSuperDialogBase) -> Boolean,
    private val isExtractInterface: Boolean,
    @Nls refactoringName: String,
    private val refactoring: (ExtractSuperInfo) -> Unit
) : JavaExtractSuperBaseDialog(originalClass.project, originalClass.toLightClass()!!, emptyList(), refactoringName) {
    private var initComplete: Boolean = false

    private lateinit var memberInfoModel: MemberInfoModelBase

    val selectedMembers: List<KotlinMemberInfo>
        get() = memberInfoModel.memberInfos.filter { it.isChecked }

    private val fileNameField = JTextField()

    open class MemberInfoModelBase(
        originalClass: KtClassOrObject,
        val memberInfos: List<KotlinMemberInfo>,
        interfaceContainmentVerifier: (KtNamedDeclaration) -> Boolean
    ) : KotlinUsesAndInterfacesDependencyMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>(
        originalClass,
        null,
        false,
        interfaceContainmentVerifier
    ) {
        override fun isMemberEnabled(member: KotlinMemberInfo): Boolean {
            val declaration = member.member ?: return false
            return !declaration.hasModifier(KtTokens.CONST_KEYWORD)
        }

        override fun isAbstractEnabled(memberInfo: KotlinMemberInfo): Boolean {
            val member = memberInfo.member
            return !(member.hasModifier(KtTokens.INLINE_KEYWORD) ||
                    member.hasModifier(KtTokens.EXTERNAL_KEYWORD) ||
                    member.hasModifier(KtTokens.LATEINIT_KEYWORD))
        }

        override fun isFixedAbstract(memberInfo: KotlinMemberInfo?): Boolean = true
    }

    val selectedTargetParent: PsiElement
        get() = if (targetParent is PsiDirectory) targetDirectory else targetParent

    val targetFileName: String
        get() = fileNameField.text

    private fun resetFileNameField() {
        if (!initComplete) return
        fileNameField.text = "$extractedSuperName.${KotlinFileType.EXTENSION}"
    }

    protected abstract fun createMemberInfoModel(): MemberInfoModelBase

    override fun getDocCommentPanelName(): String = KotlinBundle.message("title.kdoc.for.abstracts")

    override fun checkConflicts(): Boolean = conflictChecker(this)

    override fun createActionComponent(): JBBox = JBBox.createHorizontalBox()!!

    override fun createExtractedSuperNameField(): JTextField {
        return super.createExtractedSuperNameField().apply {
            onTextChange { resetFileNameField() }
        }
    }

    override fun createDestinationRootPanel(): JPanel? {
        if (targetParent !is PsiDirectory) return null

        val targetDirectoryPanel = super.createDestinationRootPanel()
        val targetFileNamePanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
            val label = JBLabel(KotlinBundle.message("label.text.target.file.name"))
            add(label, BorderLayout.NORTH)
            label.labelFor = fileNameField
            add(fileNameField, BorderLayout.CENTER)
        }

        val formBuilder = FormBuilder.createFormBuilder()
        if (targetDirectoryPanel != null) {
            formBuilder.addComponent(targetDirectoryPanel)
        }
        return formBuilder.addComponent(targetFileNamePanel).panel
    }

    override fun createNorthPanel(): JComponent? {
        return super.createNorthPanel().apply {
            if (targetParent !is PsiDirectory) {
                myPackageNameLabel.parent.remove(myPackageNameLabel)
                myPackageNameField.parent.remove(myPackageNameField)
            }
        }
    }

    override fun createCenterPanel(): JComponent? {
        memberInfoModel = createMemberInfoModel().apply {
            memberInfoChanged(MemberInfoChange(memberInfos))
        }

        return JPanel(BorderLayout()).apply {
            val memberSelectionPanel = KotlinMemberSelectionPanel(
                RefactoringBundle.message(if (isExtractInterface) "members.to.form.interface" else "members.to.form.superclass"),
                memberInfoModel.memberInfos,
                RefactoringBundle.message("make.abstract")
            )
            memberSelectionPanel.table.memberInfoModel = memberInfoModel
            memberSelectionPanel.table.addMemberInfoChangeListener(memberInfoModel)
            add(memberSelectionPanel, BorderLayout.CENTER)

            add(myDocCommentPanel, BorderLayout.EAST)
        }
    }

    override fun init() {
        super.init()

        initComplete = true

        resetFileNameField()
    }

    override fun preparePackage() {
        if (targetParent is PsiDirectory) super.preparePackage()
    }

    override fun isExtractSuperclass(): Boolean = true

    override fun validateName(name: String): String? {
        return when {
            !name.quoteIfNeeded().isIdentifier() -> RefactoringMessageUtil.getIncorrectIdentifierMessage(name)
            name.unquoteKotlinIdentifier() == mySourceClass.name -> KotlinBundle.message("error.text.different.name.expected")
            else -> null
        }
    }

    override fun createProcessor(): Nothing? = null

    override fun executeRefactoring() {
        val extractInfo = ExtractSuperInfo(
            mySourceClass.unwrapped as KtClassOrObject,
            selectedMembers,
            if (targetParent is PsiDirectory) targetDirectory else targetParent,
            targetFileName,
            extractedSuperName.quoteIfNeeded(),
            isExtractInterface,
            DocCommentPolicy(docCommentPolicy)
        )
        refactoring(extractInfo)
    }
}