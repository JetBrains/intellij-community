// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.idea.refactoring.move.mapWithReadActionInProcess
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinDestinationFolderComboBox
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinFileChooserDialog
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPureElement
import javax.swing.JComponent

class K2MoveDeclarationsDialog(
    project: Project,
    elementsToMove: Set<KtNamedDeclaration>,
    targetContainer: PsiElement?
) : RefactoringDialog(project, true) {
    private val targetDirectory = if (targetContainer is PsiDirectory) {
        targetContainer
    } else targetContainer?.containingFile?.containingDirectory

    private var selectedPackage = targetDirectory?.let { JavaDirectoryService.getInstance().getPackage(it) }?.qualifiedName

    private val sourceFiles = getSourceFiles(elementsToMove)

    private val allDeclarations = getAllDeclarations(sourceFiles)

    private val pkgChooserComboBox: PackageNameReferenceEditorCombo = PackageNameReferenceEditorCombo(
        "",
        myProject,
        RECENTS_KEY,
        RefactoringBundle.message("choose.destination.package")
    ).apply {
        prependItem(selectedPackage)
    }

    private val destinationFolderComboBox: KotlinDestinationFolderComboBox = object : KotlinDestinationFolderComboBox() {
        override fun getTargetPackage(): String {
            return pkgChooserComboBox.text
        }
    }.apply {
        setData(
            myProject,
            targetDirectory,
            { s -> setErrorText(s) },
            pkgChooserComboBox.childComponent
        )
    }

    private val fileChooser: TextFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
        addActionListener { _ ->
            val dialog = KotlinFileChooserDialog(
                KotlinBundle.message("text.choose.containing.file"),
                project,
                GlobalSearchScope.projectScope(project), pkgChooserComboBox.text
            )
            dialog.showDialog()
            val selectedFile = if (dialog.isOK) dialog.selected else null
            if (selectedFile != null) text = selectedFile.virtualFile.path
        }
    }

    private val selectionPanel: KotlinMemberSelectionPanel = KotlinMemberSelectionPanel(
        memberInfo = memberInfos(project, elementsToMove, allDeclarations)
    )

    init {
        title = MoveHandler.getRefactoringName()
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label(KotlinBundle.message("label.text.package")).align(AlignX.LEFT)
                cell(pkgChooserComboBox).align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
            row {
                label(KotlinBundle.message("label.text.destination")).align(AlignX.LEFT)
                cell(destinationFolderComboBox).align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
            row {
                label(KotlinBundle.message("label.text.file")).align(AlignX.LEFT)
                 cell(fileChooser).align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
            row {
                cell(selectionPanel).align(Align.FILL)
            }.layout(RowLayout.PARENT_GRID).resizableRow()
            row {
                panel {
                    row {
                        checkBox(KotlinBundle.message("checkbox.text.search.references"))
                    }.layout(RowLayout.PARENT_GRID)
                    row {
                        checkBox(KotlinBundle.message("checkbox.text.delete.empty.source.files"))
                    }.layout(RowLayout.PARENT_GRID)
                }.align(AlignX.LEFT)
                panel {
                    row {
                        checkBox(KotlinBundle.message("search.for.text.occurrences"))
                    }.layout(RowLayout.PARENT_GRID)
                    row {
                        checkBox(KotlinBundle.message("search.in.comments.and.strings"))
                    }.layout(RowLayout.PARENT_GRID)
                }.align(AlignX.RIGHT)
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    override fun doAction() {
        TODO("Not yet implemented")
    }

    companion object {
        private const val RECENTS_KEY = "MoveKotlinTopLevelDeclarationsDialog.RECENTS_KEY"

        private fun memberInfos(
            project: Project,
            elementsToMove: Set<KtNamedDeclaration>,
            allDeclaration: List<KtNamedDeclaration>
        ): List<KotlinMemberInfo> {
            return allDeclaration.mapWithReadActionInProcess(project, MoveHandler.getRefactoringName()) { declaration ->
                KotlinMemberInfo(declaration, false).apply { isChecked = elementsToMove.contains(declaration) }
            }
        }

        private fun getSourceFiles(elementsToMove: Collection<KtNamedDeclaration>): List<KtFile> = elementsToMove
            .map(KtPureElement::getContainingKtFile)
            .distinct()

        private fun getAllDeclarations(sourceFiles: Collection<KtFile>): List<KtNamedDeclaration> = sourceFiles
            .flatMap<KtFile, KtDeclaration> { file -> if (file.isScript()) file.script!!.declarations else file.declarations }
            .filterIsInstance(KtNamedDeclaration::class.java)
    }
}