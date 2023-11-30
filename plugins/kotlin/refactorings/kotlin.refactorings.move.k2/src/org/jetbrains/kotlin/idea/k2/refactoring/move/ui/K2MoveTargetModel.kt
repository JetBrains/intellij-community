// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.ide.util.DirectoryChooser
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.PsiDirectory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.ui.RecentsManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinDestinationFolderComboBox
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinFileChooserDialog
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import javax.swing.JComponent

sealed interface K2MoveTargetModel {
    val directory: PsiDirectory

    val pkgName: FqName

    fun toDescriptor(): K2MoveTargetDescriptor

    context(Panel)
    fun buildPanel(project: Project, onError: (String?, JComponent) -> Unit)

    open class SourceDirectory(
        override var pkgName: FqName,
        override var directory: PsiDirectory
    ) : K2MoveTargetModel {
        override fun toDescriptor(): K2MoveTargetDescriptor.SourceDirectory = K2MoveTargetDescriptor.SourceDirectory(pkgName, directory)

        context(Panel)
        override fun buildPanel(project: Project, onError: (String?, JComponent) -> Unit) {
            lateinit var pkgChooser: PackageNameReferenceEditorCombo
            row {
                label(KotlinBundle.message("label.text.package")).align(AlignX.LEFT)
                pkgChooser = cell(
                    PackageNameReferenceEditorCombo(
                        "",
                        project,
                        RECENT_PACKAGE_KEY,
                        RefactoringBundle.message("choose.destination.package")
                    )
                ).align(AlignX.FILL).component
                pkgChooser.prependItem(pkgName.asString())
            }.layout(RowLayout.PARENT_GRID)

            lateinit var destinationChooser: KotlinDestinationFolderComboBox
            row {
                label(KotlinBundle.message("label.text.destination")).align(AlignX.LEFT)
                destinationChooser = cell(object : KotlinDestinationFolderComboBox() {
                    override fun getTargetPackage(): String {
                        return pkgChooser.text
                    }
                }).align(AlignX.FILL).component
            }.layout(RowLayout.PARENT_GRID)
            destinationChooser.setData(project, directory, { s -> onError(s, destinationChooser) }, pkgChooser.childComponent)

            onApply {
                directory = (destinationChooser.comboBox.selectedItem as? DirectoryChooser.ItemWrapper?)?.directory ?: directory
                pkgName = FqName(pkgChooser.text)
                RecentsManager.getInstance(project).registerRecentEntry(RECENT_PACKAGE_KEY, destinationChooser.targetPackage)
            }
        }

        private companion object {
            const val RECENT_PACKAGE_KEY = "K2MoveDeclarationsDialog.RECENT_PACKAGE_KEY"
        }
    }

    class File(file: KtFile, pkg: FqName, directory: PsiDirectory) : SourceDirectory(pkg, directory) {
        var file: KtFile = file
            private set

        override fun toDescriptor(): K2MoveTargetDescriptor.File = K2MoveTargetDescriptor.File(file, pkgName, directory)

        context(Panel)
        override fun buildPanel(project: Project, onError: (String?, JComponent) -> Unit) {
            super.buildPanel(project, onError)
            lateinit var fileChooser: TextFieldWithBrowseButton
            row {
                label(KotlinBundle.message("label.text.file")).align(AlignX.LEFT)
                fileChooser = cell(TextFieldWithBrowseButton()).align(AlignX.FILL).component
                fileChooser.text = file.virtualFilePath
                fileChooser.addActionListener {
                    val dialog = KotlinFileChooserDialog(
                        KotlinBundle.message("text.choose.containing.file"),
                        project,
                        GlobalSearchScope.projectScope(project), pkgName.asString()
                    )
                    dialog.showDialog()
                    val selectedFile = if (dialog.isOK) dialog.selected else null
                    if (selectedFile != null) fileChooser.text = selectedFile.virtualFile.path
                }
            }.layout(RowLayout.PARENT_GRID)
            onApply {
                val selectedFile = Paths.get(fileChooser.text).toFile().toPsiFile(project) as? KtFile?
                if (selectedFile != null) file = selectedFile
            }
        }
    }

    companion object {
        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            return File(file, file.packageFqName, directory)
        }
    }
}