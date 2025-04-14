// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.ide.util.DirectoryChooser
import com.intellij.ide.util.TreeJavaClassChooserDialog
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.RecentsManager
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinDestinationFolderComboBox
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinFileChooserDialog
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

sealed interface K2MoveTargetModel {
    val directory: PsiDirectory

    val pkgName: FqName

    fun toDescriptor(): K2MoveTargetDescriptor

    fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit)

    @ApiStatus.Internal
    abstract class SourceDirectoryChooser(
        override var pkgName: FqName,
        override var directory: PsiDirectory
    ) : K2MoveTargetModel {
        private val initialDirectory = directory

        protected lateinit var pkgChooser: PackageNameReferenceEditorCombo

        protected lateinit var destinationChooser: KotlinDestinationFolderComboBox

        protected fun Panel.installPkgChooser(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = directory.project
            row(KotlinBundle.message("label.text.package")) {
                pkgChooser = cell(
                    PackageNameReferenceEditorCombo(
                        "",
                        project,
                        RECENT_PACKAGE_KEY,
                        RefactoringBundle.message("choose.destination.package")
                    )
                ).align(AlignX.FILL).resizableColumn().component.apply {
                    setTextFieldPreferredWidth(PREFERED_TEXT_WIDTH)
                }
                pkgChooser.prependItem(pkgName.asString())
            }
            row(KotlinBundle.message("label.text.destination")) {
                destinationChooser = cell(object : KotlinDestinationFolderComboBox() {
                    override fun getTargetPackage(): String {
                        return pkgChooser.text
                    }
                }).align(AlignX.FILL).component.apply {
                    setTextFieldPreferredWidth(PREFERED_TEXT_WIDTH)
                }
            }

            destinationChooser.comboBox.addPropertyChangeListener { // Invoked from package chooser update
                if (it.propertyName != "model") return@addPropertyChangeListener
                pkgName = FqName(pkgChooser.text)
                RecentsManager.getInstance(project).registerRecentEntry(RECENT_PACKAGE_KEY, pkgChooser.text)
                updateDirectory(onError, revalidateButtons)
            }
            destinationChooser.comboBox.addActionListener {
                updateDirectory(onError, revalidateButtons)
            }
            destinationChooser.setData(project, directory, { s -> onError(s, destinationChooser) }, pkgChooser.childComponent)
        }

        protected open fun updateDirectory(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = directory.project
            val selected = destinationChooser.comboBox.selectedItem as? DirectoryChooser.ItemWrapper
            if (selected == null || selected == DirectoryChooser.ItemWrapper.NULL) {
                ReadAction.nonBlocking<PsiDirectory> {
                    val projectIndex = ProjectFileIndex.getInstance(project)
                    projectIndex.getSourceRootForFile(initialDirectory.virtualFile)?.toPsiDirectory(project) ?: initialDirectory
                }.finishOnUiThread(ModalityState.stateForComponent(destinationChooser)) { rootDir ->
                    directory = rootDir
                }.submit(AppExecutorUtil.getAppExecutorService())
            } else {
                directory = selected.directory ?: directory
            }
            revalidateButtons()
        }

        private companion object {
            const val RECENT_PACKAGE_KEY = "K2MoveDeclarationsDialog.RECENT_PACKAGE_KEY"

            const val PREFERED_TEXT_WIDTH = 40
        }
    }

    open class SourceDirectory(
        pkgName: FqName,
        directory: PsiDirectory
    ) : SourceDirectoryChooser(pkgName, directory) {
        override fun toDescriptor(): K2MoveTargetDescriptor.Directory = K2MoveTargetDescriptor.Directory(pkgName, directory)

        override fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            panel.installPkgChooser(onError, revalidateButtons)
        }
    }

    @ApiStatus.Internal
    abstract class FileChooser(fileName: String, pkg: FqName, directory: PsiDirectory) : SourceDirectoryChooser(pkg, directory) {
        var fileName: String = fileName
            protected set

        private var selectedFile: KtFile? = null

        override fun updateDirectory(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            super.updateDirectory(onError, revalidateButtons)
            val selectedFile = selectedFile
            if (selectedFile != null && selectedFile.packageFqName != pkgName) {
                onError("Existing file package does not match selected package", pkgChooser)
            } else {
                onError(null, pkgChooser)
            }
        }

        protected lateinit var fileChooser: TextFieldWithBrowseButton
        protected fun Row.installFileChooser(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = directory.project
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
                selectedFile = if (dialog.isOK) dialog.selected else null
                selectedFile?.let { selectedFile ->
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

    class File(fileName: String, pkg: FqName, directory: PsiDirectory) : FileChooser(fileName, pkg, directory) {
        override fun toDescriptor(): K2MoveTargetDescriptor.File = K2MoveTargetDescriptor.File(fileName, pkgName, directory)

        override fun buildPanel(panel: Panel, onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            panel.installPkgChooser(onError, revalidateButtons)
            panel.row {
                installFileChooser(onError, revalidateButtons)
            }
        }
    }

    class Declarations(
        defaultDirectory: PsiDirectory,
        defaultPkgName: FqName,
        defaultFileName: String
    ) : FileChooser(defaultFileName, defaultPkgName, defaultDirectory) {
        private val propertyGraph = PropertyGraph()

        private val destinationClassProperty = propertyGraph.property<KtClassOrObject?>(null)
        internal var destinationClass: KtClassOrObject? by destinationClassProperty

        private val destinationTargetProperty = propertyGraph.property<MoveTargetType>(MoveTargetType.FILE)
        internal var destinationTargetType: MoveTargetType by destinationTargetProperty

        private lateinit var classChooser: TextFieldWithBrowseButton

        internal enum class MoveTargetType {
            FILE, CLASS
        }

        override fun toDescriptor(): K2MoveTargetDescriptor.Declaration<*> {
            val selectedClass = destinationClass
            return if (destinationTargetType == MoveTargetType.CLASS && selectedClass != null) {
                K2MoveTargetDescriptor.ClassOrObject(selectedClass)
            } else {
                K2MoveTargetDescriptor.File(fileName, pkgName, directory)
            }
        }

        private fun canSelectClass(clazz: PsiClass): Boolean {
            return (clazz as? KtLightClass)?.kotlinOrigin != null
        }

        private fun Row.installClassTargetChooser(onError: (String?, JComponent) -> Unit, revalidateButtons: () -> Unit) {
            val project = directory.project
            classChooser = cell(TextFieldWithBrowseButton()).align(AlignX.FILL).component
            classChooser.isEnabled = destinationTargetType == MoveTargetType.CLASS
            classChooser.text = ""

            classChooser.addActionListener {
                val chooser = TreeJavaClassChooserDialog(
                    RefactoringBundle.message("choose.destination.class"),
                    project,
                    project.projectScope().restrictToKotlinSources(),
                    ::canSelectClass,
                    null,
                    null,
                    true
                )
                chooser.showDialog()
                destinationClass = chooser.selected?.unwrapped as? KtClassOrObject
                classChooser.text = destinationClass?.fqName?.asString() ?: ""
                revalidateButtons()
            }
            classChooser.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    ReadAction.nonBlocking<PsiClass> {
                        JavaPsiFacade
                            .getInstance(project)
                            .findClass(classChooser.text, project.projectScope())
                    }.finishOnUiThread(ModalityState.stateForComponent(classChooser)) { selectedClass ->
                        destinationClass = selectedClass?.unwrapped as? KtClassOrObject
                        if (selectedClass == null) {
                            onError(KotlinBundle.message("refactoring.cannot.find.target.class"), classChooser)
                        } else {
                            onError(null, classChooser)
                        }
                        revalidateButtons()
                    }.submit(AppExecutorUtil.getAppExecutorService())
                }
            })
        }

        override fun buildPanel(
            panel: Panel,
            onError: (String?, JComponent) -> Unit,
            revalidateButtons: () -> Unit
        ) {
            panel.installPkgChooser(onError, revalidateButtons)

            if (Registry.`is`("kotlin.move.show.move.to.class")) {
                panel.buttonsGroup(indent = true) {
                    panel.row {
                        radioButton(KotlinBundle.message("refactoring.file.destination"), MoveTargetType.FILE)
                            .onChanged {
                                destinationTargetType = MoveTargetType.FILE
                                fileChooser.isEnabled = true
                                pkgChooser.isEnabled = true
                                destinationChooser.isEnabled = true
                                classChooser.isEnabled = false
                                revalidateButtons()
                            }
                        installFileChooser(onError, revalidateButtons)
                    }
                    panel.row {
                        radioButton(KotlinBundle.message("refactoring.class.destination"), MoveTargetType.CLASS)
                            .onChanged {
                                destinationTargetType = MoveTargetType.CLASS
                                fileChooser.isEnabled = false
                                pkgChooser.isEnabled = false
                                destinationChooser.isEnabled = false
                                classChooser.isEnabled = true
                                revalidateButtons()
                            }
                        installClassTargetChooser(onError, revalidateButtons)
                    }
                }.bind(::destinationTargetType.toMutableProperty())
            } else {
                panel.row {
                    installFileChooser(onError, revalidateButtons)
                }
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