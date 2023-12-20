// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.platform.backend.presentation.TargetPresentation
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
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.JComponent

sealed interface K2MoveSourceModel<T : KtElement> {
    val elements: Set<T>

    fun toDescriptor(): K2MoveSourceDescriptor<T>?

    context(Panel)
    fun buildPanel(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit)

    class FileSource(files: Set<KtFile>) : K2MoveSourceModel<KtFile> {
        override var elements: Set<KtFile> = files
            internal set

        override fun toDescriptor(): K2MoveSourceDescriptor.FileSource = K2MoveSourceDescriptor.FileSource(elements)

        context(Panel)
        override fun buildPanel(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = elements.firstOrNull()?.project ?: return

            class PresentableFile(val file: KtFile, val presentation: TargetPresentation)

            val presentableFiles = ActionUtil.underModalProgress(project, RefactoringBundle.message("move.title")) {
                elements.map { file ->
                    val presentation = TargetPresentation.builder(file.name)
                        .icon(file.getIcon(0))
                        .locationText(file.virtualFile.parent.path)
                        .presentation()
                    PresentableFile(file, presentation)
                }
            }

            group(RefactoringBundle.message("move.files.group")) {
                lateinit var list: JBList<PresentableFile>
                row {
                    list = JBList(CollectionListModel(presentableFiles))
                    list.cellRenderer = createTargetPresentationRenderer { presentableFile -> presentableFile.presentation }
                    cell(list).resizableColumn()
                }
                onApply {
                    elements = (list.model as CollectionListModel).items.map { it.file }.toSet()
                }
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).resizableRow()
        }
    }

    class ElementSource(declarations: Set<KtNamedDeclaration>) : K2MoveSourceModel<KtNamedDeclaration> {
        override var elements: Set<KtNamedDeclaration> = declarations
            private set

        private lateinit var memberSelectionPanel: KotlinMemberSelectionPanel

        override fun toDescriptor(): K2MoveSourceDescriptor.ElementSource = K2MoveSourceDescriptor.ElementSource(elements)

        context(Panel)
        override fun buildPanel(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
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
        }
    }
}