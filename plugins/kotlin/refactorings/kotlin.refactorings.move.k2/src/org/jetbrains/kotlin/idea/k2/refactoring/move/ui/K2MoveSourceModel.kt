// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.MutableBooleanProperty
import com.intellij.openapi.observable.properties.ObservableBooleanProperty
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.refactoring.memberInfo.AbstractKotlinMemberInfoModel
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.JComponent

sealed class K2MoveSourceModel<T : PsiElement>(
    observableUiSettings: ObservableUiSettings
): K2SourceModelObservableSettings {
    abstract val elements: Set<T>

    abstract fun toDescriptor(): K2MoveSourceDescriptor<T>?

    abstract fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit)

    init {
        observableUiSettings.registerK2SourceModelSettings(this)
    }

    class FileSource(
        fsItems: Set<PsiFileSystemItem>,
        observableUiSettings: ObservableUiSettings,
    ) : K2MoveSourceModel<PsiFileSystemItem>(observableUiSettings) {
        override var elements: Set<PsiFileSystemItem> = fsItems
        override val mppDeclarationsSelectedObservable: ObservableBooleanProperty = ConstantBooleanObservableProperty(false)

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
                row {
                    cell(JBList(CollectionListModel(presentableFiles)).apply {
                        cellRenderer = createTargetPresentationRenderer { it }
                    }).align(Align.FILL).component
                }.resizableRow()
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).resizableRow()
        }
    }

    class ElementSource(
        declarations: Set<KtNamedDeclaration>,
        observableUiSettings: ObservableUiSettings,
    ) : K2MoveSourceModel<KtNamedDeclaration>(observableUiSettings) {
        override var elements: Set<KtNamedDeclaration> = declarations
            private set
        override val mppDeclarationsSelectedObservable: MutableBooleanProperty =
            AtomicBooleanProperty(hasExpectOrActualElements())

        private lateinit var memberSelectionPanel: KotlinMemberSelectionPanel

        override fun toDescriptor(): K2MoveSourceDescriptor.ElementSource = K2MoveSourceDescriptor.ElementSource(elements)

        override fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = elements.firstOrNull()?.project ?: return
            val isNestedDeclarationMove = isNestedDeclarationMove()
            val memberInfoModel = if (isNestedDeclarationMove) ReadOnlyKotlinMemberInfoModel else null
            val memberInfos = ActionUtil.underModalProgress(project, RefactoringBundle.message("move.title")) {
                val containers = getDeclarationsContainers(elements)
                val allDeclarations = getAllDeclarations(containers)
                val shownDeclarations = if (isNestedDeclarationMove) allDeclarations.filter { it in elements } else allDeclarations
                return@underModalProgress getMemberInfos(elements, shownDeclarations.toList())
            }

            panel.group(RefactoringBundle.message("move.declarations.group"), indent = false) {
                row {
                    val selectionPanel = KotlinMemberSelectionPanel(memberInfo = memberInfos, memberInfoModel = memberInfoModel)
                    memberSelectionPanel = cell(selectionPanel).align(Align.FILL).component
                    val table = memberSelectionPanel.table
                    table.addMemberInfoChangeListener {
                        elements = table.selectedMemberInfos.map { it.member }.toSet()
                        mppDeclarationsSelectedObservable.set(hasExpectOrActualElements())
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

        private fun isNestedDeclarationMove(): Boolean {
            val singleElement = elements.singleOrNull() ?: return false
            return singleElement.parent is KtClassBody
        }

        private fun getDeclarationsContainers(elementsToMove: Collection<KtNamedDeclaration>): Set<KtDeclarationContainer> = elementsToMove
            .mapNotNull { it.parent as? KtDeclarationContainer }
            .toSet()

        private fun getAllDeclarations(container: Collection<KtDeclarationContainer>): Set<KtNamedDeclaration> = container
            .flatMap { it.declarations }
            .filterIsInstance<KtNamedDeclaration>()
            .toSet()

        private fun getMemberInfos(
            elementsToMove: Set<KtNamedDeclaration>,
            allDeclaration: List<KtNamedDeclaration>
        ): List<KotlinMemberInfo> = allDeclaration.map { declaration ->
            KotlinMemberInfo(declaration, false).apply {
                isChecked = elementsToMove.contains(declaration)
            }
        }

        private fun hasExpectOrActualElements(): Boolean =
            elements.any { it.isExpectOrActual() }
    }
}

private object ReadOnlyKotlinMemberInfoModel : AbstractKotlinMemberInfoModel() {
    override fun isMemberEnabled(member: KotlinMemberInfo): Boolean = false

    override fun isCheckedWhenDisabled(member: KotlinMemberInfo?): Boolean {
        return member?.isChecked ?: false
    }
}
