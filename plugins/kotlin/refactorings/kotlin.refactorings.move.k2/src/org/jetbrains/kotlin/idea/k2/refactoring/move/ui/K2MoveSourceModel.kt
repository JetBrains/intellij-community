// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.list.createTargetPresentationRenderer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.JComponent

sealed interface K2MoveSourceModel<T : PsiElement> {
    val elements: Set<T>

    fun toDescriptor(): K2MoveSourceDescriptor<T>?

    fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit)

    class FileSource(fsItems: Set<PsiFileSystemItem>) : K2MoveSourceModel<PsiFileSystemItem> {
        override var elements: Set<PsiFileSystemItem> = fsItems

        override fun toDescriptor(): K2MoveSourceDescriptor.FileSource = K2MoveSourceDescriptor.FileSource(elements)

        override fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = elements.firstOrNull()?.project ?: return

            val presentableFiles = ActionUtil.underModalProgress(project, RefactoringBundle.message("move.title")) {
                elements.map { file ->
                    TargetPresentation.builder(file.name)
                        .icon(file.getIcon(0))
                        .presentation()
                }
            }

            panel.group(RefactoringBundle.message("move.files.group")) {
                lateinit var list: JBList<TargetPresentation>
                row {
                    list = cell(JBList(CollectionListModel(presentableFiles)).apply {
                        cellRenderer = createTargetPresentationRenderer { it }
                    }).align(Align.FILL).component
                }.resizableRow()
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).resizableRow()
        }
    }

    class ElementSource(declarations: Set<KtNamedDeclaration>) : K2MoveSourceModel<KtNamedDeclaration> {
        override var elements: Set<KtNamedDeclaration> = declarations
            private set

        private lateinit var memberSelectionPanel: KotlinMemberSelectionPanel

        override fun toDescriptor(): K2MoveSourceDescriptor.ElementSource = K2MoveSourceDescriptor.ElementSource(elements)

        private fun isNestedDeclarationMove(): Boolean {
            val singleElement = elements.singleOrNull() ?: return false
            return singleElement.parent is KtClassBody
        }

        override fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            if (isNestedDeclarationMove()) {
                // We only want to move a single declaration, no choosing necessary.
                return
            }

            fun getDeclarationsContainers(elementsToMove: Collection<KtNamedDeclaration>): Set<KtDeclarationContainer> = elementsToMove
                .mapNotNull { it.parent as? KtDeclarationContainer }
                .toSet()

            fun getAllDeclarations(container: Collection<KtDeclarationContainer>): Set<KtNamedDeclaration> = container
                .flatMap { it.declarations }
                .filterIsInstance<KtNamedDeclaration>()
                .toSet()

            fun memberInfos(
                elementsToMove: Set<KtNamedDeclaration>,
                allDeclaration: List<KtNamedDeclaration>
            ): List<KotlinMemberInfo> = allDeclaration.map { declaration ->
                KotlinMemberInfo(declaration, false).apply {
                    isChecked = elementsToMove.contains(declaration)
                }
            }

            val project = elements.firstOrNull()?.project ?: return

            val memberInfos = ActionUtil.underModalProgress(project, RefactoringBundle.message("move.title")) {
                val containers = getDeclarationsContainers(elements)
                val allDeclarations = getAllDeclarations(containers)
                return@underModalProgress memberInfos(elements, allDeclarations.toList())
            }

            panel.group(RefactoringBundle.message("move.declarations.group"), indent = false) {
                row {
                    memberSelectionPanel = cell(KotlinMemberSelectionPanel(memberInfo = memberInfos)).align(Align.FILL).component
                    val table = memberSelectionPanel.table
                    table.addMemberInfoChangeListener {
                        elements = table.selectedMemberInfos.map { it.member }.toSet()
                        if (elements.isEmpty()) {
                            onError(KotlinBundle.message("text.no.elements.to.move.are.selected"), memberSelectionPanel.table)
                        } else {
                            onError(null, memberSelectionPanel.table)
                        }
                        revalidateButtons()
                    }
                }.resizableRow()
            }.topGap(TopGap.NONE).bottomGap(BottomGap.SMALL).resizableRow()
        }
    }
}