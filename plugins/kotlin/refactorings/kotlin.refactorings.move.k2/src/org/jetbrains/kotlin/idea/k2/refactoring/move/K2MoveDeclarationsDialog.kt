// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.RecentsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveDeclarationDelegate
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveSource
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveTarget
import org.jetbrains.kotlin.idea.refactoring.move.MoveDeclarationsDescriptor
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinDestinationFolderComboBox
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinFileChooserDialog
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPureElement
import javax.swing.JComponent

class K2MoveDeclarationsDialog(
    project: Project,
    private val toDirectory: PsiDirectory?,
    private val toPackage: PsiPackage?,
    private val memberInfos: List<KotlinMemberInfo>
) : RefactoringDialog(project, true) {
    private lateinit var packageChooser: PackageNameReferenceEditorCombo

    private lateinit var destinationChooser: KotlinDestinationFolderComboBox

    private lateinit var fileChooser: TextFieldWithBrowseButton

    private lateinit var selectionPanel: KotlinMemberSelectionPanel

    private lateinit var searchReferencesCb: JBCheckBox

    private lateinit var deleteEmptySourceFilesCb: JBCheckBox

    private lateinit var searchTextOccurrencesCb: JBCheckBox

    private lateinit var searchCommentsAndStringsCb: JBCheckBox

    private lateinit var mainPanel: DialogPanel

    init {
        title = MoveHandler.getRefactoringName()
        init()
    }

    override fun createCenterPanel(): JComponent {
        mainPanel = panel {
            row {
                label(KotlinBundle.message("label.text.package")).align(AlignX.LEFT)
                packageChooser = cell(PackageNameReferenceEditorCombo(
                    "",
                    myProject,
                    RECENT_PACKAGE_KEY,
                    RefactoringBundle.message("choose.destination.package")
                )).align(AlignX.FILL).component
                packageChooser.prependItem(toPackage?.qualifiedName)
            }.layout(RowLayout.PARENT_GRID)
            row {
                label(KotlinBundle.message("label.text.destination")).align(AlignX.LEFT)
                destinationChooser = cell(object : KotlinDestinationFolderComboBox() {
                    override fun getTargetPackage(): String {
                        return packageChooser.text
                    }
                }).align(AlignX.FILL).component
            }.layout(RowLayout.PARENT_GRID)
            row {
                label(KotlinBundle.message("label.text.file")).align(AlignX.LEFT)
                fileChooser = cell(TextFieldWithBrowseButton()).align(AlignX.FILL).component
                fileChooser.addActionListener {
                    val dialog = KotlinFileChooserDialog(
                        KotlinBundle.message("text.choose.containing.file"),
                        project,
                        GlobalSearchScope.projectScope(project), packageChooser.text
                    )
                    dialog.showDialog()
                    val selectedFile = if (dialog.isOK) dialog.selected else null
                    if (selectedFile != null) fileChooser.text = selectedFile.virtualFile.path
                }
            }.layout(RowLayout.PARENT_GRID)
            row {
                selectionPanel = cell(KotlinMemberSelectionPanel(memberInfo = memberInfos)).align(Align.FILL).component
            }.layout(RowLayout.PARENT_GRID).resizableRow()
            val refactoringSettings = KotlinCommonRefactoringSettings.getInstance()
            row {
                panel {
                    row {
                        searchReferencesCb = checkBox(KotlinBundle.message("checkbox.text.search.references"))
                            .bindSelected(KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_REFERENCES)
                            .component
                    }.layout(RowLayout.PARENT_GRID)
                    row {
                        deleteEmptySourceFilesCb = checkBox(KotlinBundle.message("checkbox.text.delete.empty.source.files"))
                            .bindSelected(KotlinCommonRefactoringSettings.getInstance()::MOVE_DELETE_EMPTY_SOURCE_FILES)
                            .component
                    }.layout(RowLayout.PARENT_GRID)
                }.align(AlignX.LEFT)
                panel {
                    row {
                        searchTextOccurrencesCb = checkBox(KotlinBundle.message("search.for.text.occurrences"))
                            .bindSelected(KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_FOR_TEXT)
                            .component
                    }.layout(RowLayout.PARENT_GRID)
                    row {
                        searchCommentsAndStringsCb = checkBox(KotlinBundle.message("search.in.comments.and.strings"))
                            .bindSelected(KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_IN_COMMENTS)
                            .component
                        searchTextOccurrencesCb.isSelected = refactoringSettings.MOVE_SEARCH_IN_COMMENTS
                    }.layout(RowLayout.PARENT_GRID)
                }.align(AlignX.RIGHT)
            }.layout(RowLayout.PARENT_GRID)
        }
        return mainPanel
    }

    fun setData() {
        destinationChooser.setData(myProject, toDirectory, { s -> setErrorText(s) }, packageChooser.childComponent)
    }

    private fun saveSettings() {
        mainPanel.apply()
        KotlinCommonRefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages
        RecentsManager.getInstance(myProject).registerRecentEntry(RECENT_PACKAGE_KEY, destinationChooser.targetPackage)
    }

    private val elementsToMove: List<KtNamedDeclaration> get() {
        val elementsToMove= selectionPanel.table.selectedMemberInfos?.map(KotlinMemberInfo::getMember) ?: return emptyList()
        if (elementsToMove.isEmpty()) {
            throw ConfigurationException(KotlinBundle.message("text.no.elements.to.move.are.selected"))
        }
        return elementsToMove
    }

    override fun doAction() {
        saveSettings()
        val moveDescriptor = MoveDeclarationsDescriptor(
            project,
            KotlinMoveSource(elementsToMove),
            KotlinMoveTarget.DeferredFile(FqName(packageChooser.text), toDirectory?.virtualFile),
            KotlinMoveDeclarationDelegate.TopLevel,
            searchReferencesCb.isSelected,
            searchTextOccurrencesCb.isSelected,
            deleteEmptySourceFilesCb.isSelected,
            moveCallback = null,
            openInEditor = true,
            analyzeConflicts = true,
            searchReferences = true
        )
        val refactoringProcessor = K2MoveRefactoringProcessor(moveDescriptor)
        invokeRefactoring(refactoringProcessor)
    }

    companion object {
        private const val RECENT_PACKAGE_KEY = "K2MoveDeclarationsDialog.RECENT_PACKAGE_KEY"

        @RequiresEdt
        fun createAndShow(
            project: Project,
            elementsToMove: Set<KtNamedDeclaration>,
            targetContainer: PsiElement?
        ) {
            fun getSourceFiles(elementsToMove: Collection<KtNamedDeclaration>): List<KtFile> = elementsToMove
                .map(KtPureElement::getContainingKtFile)
                .distinct()

            fun getAllDeclarations(sourceFiles: Collection<KtFile>): List<KtNamedDeclaration> = sourceFiles
                .flatMap<KtFile, KtDeclaration> { file -> if (file.isScript()) file.script!!.declarations else file.declarations }
                .filterIsInstance(KtNamedDeclaration::class.java)

            fun memberInfos(
                elementsToMove: Set<KtNamedDeclaration>,
                allDeclaration: List<KtNamedDeclaration>
            ): List<KotlinMemberInfo> = allDeclaration.map { declaration ->
                KotlinMemberInfo(declaration, false).apply {
                    isChecked = elementsToMove.contains(declaration)
                }
            }

            class K2MoveDeclarationsDialogInfo(
                val toDirectory: PsiDirectory?,
                val toPackage: PsiPackage?,
                val memberInfos: List<KotlinMemberInfo>
            )

            val dialogInfo = ActionUtil.underModalProgress(project, RefactoringBundle.message("move.title")) {
                val toDirectory =
                    if (targetContainer is PsiDirectory) targetContainer else targetContainer?.containingFile?.containingDirectory
                val toPackage = toDirectory?.let { JavaDirectoryService.getInstance().getPackage(it) }
                val sourceFiles = getSourceFiles(elementsToMove)
                val allDeclarations = getAllDeclarations(sourceFiles)
                val memberInfos = memberInfos(elementsToMove, allDeclarations)
                return@underModalProgress K2MoveDeclarationsDialogInfo(toDirectory, toPackage, memberInfos)
            }

            K2MoveDeclarationsDialog(project, dialogInfo.toDirectory, dialogInfo.toPackage, dialogInfo.memberInfos).apply {
                setData()
            }.show()
        }
    }
}