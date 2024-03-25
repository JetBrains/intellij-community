// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KMutableProperty0

/**
 * @see org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
 */
sealed class K2MoveModel {
    abstract val project: Project

    abstract val source: K2MoveSourceModel<*>

    abstract val target: K2MoveTargetModel

    val searchForText: Setting = Setting.SEARCH_FOR_TEXT

    val searchInComments: Setting = Setting.SEARCH_IN_COMMENTS

    abstract val inSourceRoot: Boolean

    val searchReferences: Setting = Setting.SEARCH_REFERENCES

    abstract fun toDescriptor(): K2MoveDescriptor

    fun isValidRefactoring(): Boolean {
        fun KtFile.isTargetFile(): Boolean {
            return (target as? K2MoveTargetModel.File)?.let { fileTarget ->
                containingDirectory == fileTarget.directory && name == fileTarget.fileName
            } ?: false
        }
        if (source.elements.isEmpty()) return false
        val files = source.elements.map { it.containingKtFile }.toSet()
        return files.size != 1 || !files.single().isTargetFile()
    }

    enum class Setting(private val text: @NlsContexts.Checkbox String, val setting: KMutableProperty0<Boolean>) {
        SEARCH_FOR_TEXT(
            KotlinBundle.message("search.for.text.occurrences"),
            KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_FOR_TEXT
        ),

        SEARCH_IN_COMMENTS(
            KotlinBundle.message("search.in.comments.and.strings"),
            KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_IN_COMMENTS
        ),

        SEARCH_REFERENCES(
            KotlinBundle.message("checkbox.text.search.references"),
            KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_REFERENCES
        );

        var state: Boolean = setting.get()
            private set

        context(Panel)
        fun createComboBox() {
            row {
                checkBox(text).bindSelected(::state)
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    /**
     * @see org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor.Files
     */
    class Files(
        override val project: Project,
        override val source: K2MoveSourceModel.FileSource,
        override val target: K2MoveTargetModel.SourceDirectory,
        override val inSourceRoot: Boolean,
    ) : K2MoveModel() {
        override fun toDescriptor(): K2MoveDescriptor {
            val srcDescr = source.toDescriptor()
            val targetDescr = target.toDescriptor()
            val searchReferences = if (inSourceRoot) searchReferences.state else false
            return K2MoveDescriptor.Files(
                project,
                srcDescr,
                targetDescr,
                searchForText.state,
                searchReferences,
                searchInComments.state
            )
        }
    }

    /**
     * @see org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor.Declarations
     */
    class Declarations(
        override val project: Project,
        override val source: K2MoveSourceModel.ElementSource,
        override val target: K2MoveTargetModel.File,
        override val inSourceRoot: Boolean,
    ) : K2MoveModel() {
        override fun toDescriptor(): K2MoveDescriptor {
            val srcDescr = source.toDescriptor()
            val targetDescr = target.toDescriptor()
            val searchReferences = if (inSourceRoot) searchReferences.state else false
            return K2MoveDescriptor.Declarations(
                project,
                srcDescr,
                targetDescr,
                searchForText.state,
                searchReferences,
                searchInComments.state
            )
        }
    }

    companion object {
        private val MOVE_DECLARATIONS: String
            @Nls
            get() = KotlinBundle.message("text.move.declarations")

        fun create(
            elements: Array<out KtElement>,
            targetContainer: PsiElement?,
            editor: Editor? = null
        ): K2MoveModel? {
            val project = elements.firstOrNull()?.project ?: error("Elements not part of project")

            fun inSourceRoot(): Boolean {
                val sourceFiles = elements.map { it.containingFile }.toSet()
                val fileIndex = ProjectFileIndex.getInstance(project)
                if (sourceFiles.any { !fileIndex.isInSourceContent(it.virtualFile) }) return false
                if (targetContainer == null || targetContainer is PsiDirectory) return true
                val targetFile = targetContainer.containingFile?.virtualFile ?: return false
                return fileIndex.isInSourceContent(targetFile)
            }

            if (elements.any { it.parentOfType<KtNamedDeclaration>(withSelf = false) != null }) {
                val message = RefactoringBundle.getCannotRefactorMessage(
                    KotlinBundle.message("text.move.declaration.no.support.for.nested.declarations")
                )
                CommonRefactoringUtil.showErrorHint(project, editor, message, MOVE_DECLARATIONS, null)
                return null
            }

            if (elements.any { it is KtEnumEntry }) {
                val message = RefactoringBundle.getCannotRefactorMessage(KotlinBundle.message("text.move.declaration.no.support.for.enums"))
                CommonRefactoringUtil.showErrorHint(project, editor, message, MOVE_DECLARATIONS, null)
                return null
            }

            if (elements.map { it.containingKtFile }.size > 1 && targetContainer != null && targetContainer !is PsiDirectory) {
                val message = RefactoringBundle.getCannotRefactorMessage(KotlinBundle.message("text.move.file.no.support.for.file.target"))
                CommonRefactoringUtil.showErrorHint(project, editor, message, MOVE_DECLARATIONS, null)
                return null
            }

            fun PsiElement?.isSingleClassContainer(): Boolean {
                if (this !is KtClass) return false
                val file = parent as? KtFile ?: return false
                return this == file.declarations.singleOrNull()
            }

            fun isSingleFileMove(movedElement: Array<out KtElement>) = movedElement.all { it is KtNamedDeclaration }
                    || movedElement.singleOrNull() is KtFile

            fun isMultiFileMove(movedElement: Array<out KtElement>) = movedElement.map { it.containingFile }.toSet().size > 1

            val inSourceRoot = inSourceRoot()
            return when {
                targetContainer is PsiDirectory || isMultiFileMove(elements) -> {
                    val source = K2MoveSourceModel.FileSource(elements.map { it.containingKtFile }.toSet())
                    val target = if (targetContainer is PsiDirectory) {
                        val pkg = targetContainer.getFqNameWithImplicitPrefixOrRoot()
                        K2MoveTargetModel.SourceDirectory(pkg, targetContainer)
                    } else { // no default target is provided, happens when invoking refactoring via keyboard instead of drag-and-drop
                        val file = elements.firstOrNull()?.containingKtFile ?: error("No default target found")
                        val directory = file.containingDirectory ?: error("No default target found")
                        val pkgName = elements.map { it.containingKtFile.packageFqName }.toSet().firstOrNull() ?: FqName.ROOT
                        K2MoveTargetModel.SourceDirectory(pkgName, directory)
                    }
                    Files(project, source, target, inSourceRoot)
                }
                targetContainer is KtFile || targetContainer.isSingleClassContainer() || isSingleFileMove(elements) -> {
                    val elementsFromFiles = elements.flatMap { elem ->
                        when (elem) {
                            is KtFile -> elem.declarations.filterIsInstance<KtNamedDeclaration>()
                            is KtNamedDeclaration -> listOf(elem)
                            else -> error("Element to move should be a file or declaration")
                        }
                    }.toSet()
                    val source = K2MoveSourceModel.ElementSource(elementsFromFiles)
                    val targetFile = targetContainer?.containingFile
                    val target = if (targetFile is KtFile) {
                        K2MoveTargetModel.File(targetFile)
                    } else { // no default target is provided, happens when invoking refactoring via keyboard instead of drag-and-drop
                        val firstElem = elements.firstOrNull()
                        val fileName = when (firstElem) {
                            is KtFile -> firstElem.name
                            is KtNamedDeclaration -> "${firstElem.name}.${KotlinLanguage.INSTANCE.associatedFileType?.defaultExtension}"
                            else -> error("Element to move should be a file or declaration")
                        }
                        val containingFile = firstElem.containingKtFile
                        val psiDirectory = containingFile.containingDirectory ?: error("No directory found")
                        K2MoveTargetModel.File(fileName, containingFile.packageFqName, psiDirectory)
                    }
                    Declarations(project, source, target, inSourceRoot)
                }
                else -> error("Unsupported move operation")
            }
        }
    }
}