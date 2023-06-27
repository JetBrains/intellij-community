// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.ide.util.DirectoryChooser
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.ui.RecentsManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinDestinationFolderComboBox
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinFileChooserDialog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import java.nio.file.Paths
import javax.swing.JComponent

sealed interface K2MoveTarget {
    val directory: PsiDirectory

    val pkg: PsiPackage

    context(Panel)
    fun buildPanel(onError: (String?, JComponent) -> Unit)

    open class SourceDirectory(pkg: PsiPackage, directory: PsiDirectory) : K2MoveTarget {
        final override var pkg: PsiPackage = pkg
            private set

        final override var directory: PsiDirectory = directory
            private set

        context(Panel)
        override fun buildPanel(onError: (String?, JComponent) -> Unit) {
            val project = pkg.project

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
                pkgChooser.prependItem(pkg.qualifiedName)
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
                (destinationChooser.comboBox.selectedItem as DirectoryChooser.ItemWrapper).directory?.let { chosen ->
                    directory = chosen
                }
                directory.module?.let { module ->
                    pkg = KotlinJavaPsiFacade.getInstance(project).findPackage(pkgChooser.text, GlobalSearchScope.moduleScope(module))
                }
                RecentsManager.getInstance(project).registerRecentEntry(RECENT_PACKAGE_KEY, destinationChooser.targetPackage)
            }
        }

        private companion object {
            const val RECENT_PACKAGE_KEY = "K2MoveDeclarationsDialog.RECENT_PACKAGE_KEY"
        }
    }

    class File(file: KtFile, pkg: PsiPackage, directory: PsiDirectory) : SourceDirectory(pkg, directory) {
        var file: KtFile = file
            private set

        context(Panel)
        override fun buildPanel(onError: (String?, JComponent) -> Unit) {
            super.buildPanel(onError)
            lateinit var fileChooser: TextFieldWithBrowseButton
            val project = file.project
            row {
                label(KotlinBundle.message("label.text.file")).align(AlignX.LEFT)
                fileChooser = cell(TextFieldWithBrowseButton()).align(AlignX.FILL).component
                fileChooser.text = file.virtualFilePath
                fileChooser.addActionListener {
                    val dialog = KotlinFileChooserDialog(
                        KotlinBundle.message("text.choose.containing.file"),
                        project,
                        GlobalSearchScope.projectScope(project), pkg.text
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
        fun SourceDirectory(directory: PsiDirectory): SourceDirectory {
            val pkg = JavaDirectoryService.getInstance().getPackage(directory) ?: error("No package was found")
            return SourceDirectory(pkg, directory)
        }

        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            val pkgDirective = file.packageDirective?.fqName
            val pkg = JavaPsiFacade.getInstance(file.project).findPackage(pkgDirective?.asString() ?: "")
                      ?: error("No package was found")
            return File(file, pkg, directory)
        }
    }
}