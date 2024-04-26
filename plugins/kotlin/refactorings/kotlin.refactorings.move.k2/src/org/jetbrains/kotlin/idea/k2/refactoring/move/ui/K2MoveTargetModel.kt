// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.ide.util.DirectoryChooser
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.RecentsManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinDestinationFolderComboBox
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinFileChooserDialog
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

sealed interface K2MoveTargetModel {
    val directory: PsiDirectory

    val pkgName: FqName

    fun toDescriptor(): K2MoveTargetDescriptor

    context(Panel)
    fun buildPanel(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit)

    open class SourceDirectory(
        override var pkgName: FqName,
        override var directory: PsiDirectory
    ) : K2MoveTargetModel {
        override fun toDescriptor(): K2MoveTargetDescriptor.SourceDirectory = K2MoveTargetDescriptor.SourceDirectory(pkgName, directory)

        protected lateinit var pkgChooser: PackageNameReferenceEditorCombo

        protected lateinit var destinationChooser: KotlinDestinationFolderComboBox

        context(Panel)
        override fun buildPanel(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = directory.project
            row(KotlinBundle.message("label.text.package")) {
                pkgChooser = cell(
                    PackageNameReferenceEditorCombo(
                        "",
                        project,
                        RECENT_PACKAGE_KEY,
                        RefactoringBundle.message("choose.destination.package")
                    )
                ).align(AlignX.FILL).component
                pkgChooser.prependItem(pkgName.asString())
            }
            row(KotlinBundle.message("label.text.destination")) {
                destinationChooser = cell(object : KotlinDestinationFolderComboBox() {
                    override fun getTargetPackage(): String {
                        return pkgChooser.text
                    }
                }).align(AlignX.FILL).component
            }

            fun updateDirectory() {
                directory = (destinationChooser.comboBox.selectedItem as? DirectoryChooser.ItemWrapper?)?.directory ?: directory
                revalidateButtons()
            }

            destinationChooser.comboBox.addPropertyChangeListener { // Invoked from package chooser update
                if (it.propertyName != "model") return@addPropertyChangeListener
                pkgName = FqName(pkgChooser.text)
                RecentsManager.getInstance(project).registerRecentEntry(RECENT_PACKAGE_KEY, pkgChooser.text)
                updateDirectory()
            }
            destinationChooser.comboBox.addActionListener {
                updateDirectory()
            }
            destinationChooser.setData(project, directory, { s -> onError(s, destinationChooser) }, pkgChooser.childComponent)
        }

        private companion object {
            const val RECENT_PACKAGE_KEY = "K2MoveDeclarationsDialog.RECENT_PACKAGE_KEY"
        }
    }

    class File(fileName: String, pkg: FqName, directory: PsiDirectory) : SourceDirectory(pkg, directory) {
        var fileName: String = fileName
            private set

        override fun toDescriptor(): K2MoveTargetDescriptor.File = K2MoveTargetDescriptor.File(fileName, pkgName, directory)

        private lateinit var fileChooser: TextFieldWithBrowseButton

        context(Panel)
        override fun buildPanel(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            super.buildPanel(onError, revalidateButtons)
            val project = directory.project
            row(KotlinBundle.message("label.text.file")) {
                fileChooser = cell(TextFieldWithBrowseButton()).align(AlignX.FILL).component
                fileChooser.text = fileName
                fileChooser.addActionListener {
                    val dialog = KotlinFileChooserDialog(
                        KotlinBundle.message("text.choose.containing.file"),
                        project,
                        project.projectScope().restrictToKotlinSources(),
                        null
                    )
                    dialog.showDialog()
                    val selectedFile = if (dialog.isOK) dialog.selected else null
                    if (selectedFile != null) {
                        fileChooser.text = selectedFile.name
                        pkgChooser.prependItem(selectedFile.packageFqName.asString())
                        ReadAction.nonBlocking<VirtualFile> {
                            ProjectFileIndex.getInstance(project).getSourceRootForFile(selectedFile.virtualFile)
                        }.finishOnUiThread(ModalityState.stateForComponent(destinationChooser)) { root ->
                            destinationChooser.selectRoot(project, root)
                        }.submit(AppExecutorUtil.getAppExecutorService())
                    }
                }
                fileChooser.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) {
                        fileName = fileChooser.text
                        if (!fileName.isValidKotlinFile()) {
                            onError(KotlinBundle.message("refactoring.move.non.kotlin.file"), fileChooser)
                        } else {
                            onError(null, fileChooser)
                        }
                        revalidateButtons()
                    }
                })
            }
        }
    }

    companion object {
        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            return File(file.name, file.packageFqName, directory)
        }
    }
}