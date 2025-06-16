// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pushDown

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.*
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinUsesDependencyMemberInfoModel
import org.jetbrains.kotlin.idea.refactoring.memberInfo.qualifiedClassNameForRendering
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class KotlinPushDownDialog(
    project: Project,
    private val memberInfos: List<KotlinMemberInfo>,
    private val sourceClass: KtClass,
) : RefactoringDialog(project, true) {
    init {
        title = PUSH_MEMBERS_DOWN
        init()
    }

    private var memberInfoModel: MemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>? = null

    private val selectedMemberInfos: List<KotlinMemberInfo>
        get() = memberInfos.filter { it.isChecked && memberInfoModel?.isMemberEnabled(it) ?: false }

    override fun getDimensionServiceKey(): String = "#" + this::class.java.name

    override fun createNorthPanel(): JComponent {
        val gbConstraints = GridBagConstraints()

        val panel = JPanel(GridBagLayout())

        gbConstraints.insets = JBUI.insets(4, 0, 10, 8)
        gbConstraints.weighty = 1.0
        gbConstraints.weightx = 1.0
        gbConstraints.gridy = 0
        gbConstraints.gridwidth = GridBagConstraints.REMAINDER
        gbConstraints.fill = GridBagConstraints.BOTH
        gbConstraints.anchor = GridBagConstraints.WEST
        panel.add(
            JLabel(
                RefactoringBundle.message(
                    "push.members.from.0.down.label",
                    sourceClass.qualifiedClassNameForRendering()
                )
            ), gbConstraints
        )
        return panel
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val memberSelectionPanel = KotlinMemberSelectionPanel(
            RefactoringBundle.message("members.to.be.pushed.down.panel.title"),
            memberInfos,
            RefactoringBundle.message("keep.abstract.column.header")
        )
        panel.add(memberSelectionPanel, BorderLayout.CENTER)

        memberInfoModel = object : DelegatingMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>(
            ANDCombinedMemberInfoModel(
                KotlinUsesDependencyMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>(sourceClass, null, false),
                UsedByDependencyMemberInfoModel<KtNamedDeclaration, PsiNamedElement, KotlinMemberInfo>(sourceClass)
            )
        ) {
            override fun isFixedAbstract(member: KotlinMemberInfo?) = null

            override fun isAbstractEnabled(memberInfo: KotlinMemberInfo): Boolean {
                val member = memberInfo.member
                if (member.hasModifier(KtTokens.INLINE_KEYWORD) ||
                    member.hasModifier(KtTokens.EXTERNAL_KEYWORD) ||
                    member.hasModifier(KtTokens.LATEINIT_KEYWORD)
                ) return false
                return member is KtNamedFunction || member is KtProperty
            }
        }
        memberInfoModel!!.memberInfoChanged(MemberInfoChange(memberInfos))
        memberSelectionPanel.table.memberInfoModel = memberInfoModel
        memberSelectionPanel.table.addMemberInfoChangeListener(memberInfoModel)

        return panel
    }

    override fun doAction() {
        if (!isOKActionEnabled) return

        KotlinCommonRefactoringSettings.getInstance().PUSH_DOWN_PREVIEW_USAGES = isPreviewUsages

        val processor = KotlinPushDownProcessorProvider.getInstance()
            .createPushDownProcessor(
                project,
                sourceClass,
                selectedMemberInfos
            )

        invokeRefactoring(processor)
    }

    override fun hasHelpAction(): Boolean = false
}