// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.copy

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler
import com.intellij.refactoring.copy.CopyHandlerDelegateBase
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

val copyCommandName get() = RefactoringBundle.message("copy.handler.copy.files.directories")

@set:TestOnly
var Project.copyNewName: String? by UserDataProperty(Key.create("NEW_NAME"))

fun PsiElement.getCopyableElement() =
    parentsWithSelf.firstOrNull { it is KtFile || (it is KtNamedDeclaration && it.parent is KtFile) } as? KtElement

fun PsiElement.getDeclarationsToCopy(): List<KtElement> = when (val declarationOrFile = getCopyableElement()) {
    is KtFile -> declarationOrFile.declarations.filterIsInstance<KtNamedDeclaration>().ifEmpty { listOf(declarationOrFile) }
    is KtNamedDeclaration -> listOf(declarationOrFile)
    else -> emptyList()
}

abstract class AbstractCopyKotlinDeclarationsHandler : CopyHandlerDelegateBase() {
    protected val copyFilesHandler by lazy { CopyFilesOrDirectoriesHandler() }

    protected fun getSourceFiles(elements: Array<out PsiElement>): Array<PsiFileSystemItem>? {
        return elements
            .map { it.containingFile ?: it as? PsiFileSystemItem ?: return null }
            .toTypedArray()
    }

    private fun canCopyFiles(elements: Array<out PsiElement>, fromUpdate: Boolean): Boolean {
        val sourceFiles = getSourceFiles(elements) ?: return false
        if (!sourceFiles.any { it is KtFile }) return false
        return copyFilesHandler.canCopy(sourceFiles, fromUpdate)
    }

    protected fun canCopyDeclarations(elements: Array<out PsiElement>): Boolean {
        val containingFile =
            elements
                .flatMap { it.getDeclarationsToCopy().ifEmpty { return false } }
                .distinctBy { it.containingFile }
                .singleOrNull()
                ?.containingFile ?: return false
        return containingFile.sourceRoot != null
    }

    override fun canCopy(elements: Array<out PsiElement>, fromUpdate: Boolean): Boolean {
        return canCopyDeclarations(elements) || canCopyFiles(elements, fromUpdate)
    }

    enum class ExistingFilePolicy {
        APPEND, OVERWRITE, SKIP
    }

    protected fun getOrCreateTargetFile(
        originalFile: KtFile,
        targetDirectory: PsiDirectory,
        targetFileName: String
    ): KtFile? {
        val existingFile = targetDirectory.findFile(targetFileName)
        if (existingFile == originalFile) return null
        if (existingFile != null) when (getFilePolicy(existingFile, targetFileName, targetDirectory)) {
            ExistingFilePolicy.APPEND -> {
            }

            ExistingFilePolicy.OVERWRITE -> runWriteAction { existingFile.delete() }
            ExistingFilePolicy.SKIP -> return null
        }
        return runWriteAction {
            if (existingFile != null && existingFile.isValid) {
                existingFile as KtFile
            } else {
                createFile(targetFileName, targetDirectory)
            }
        }
    }

    abstract fun createFile(
        targetFileName: String,
        targetDirectory: PsiDirectory
    ): KtFile

    private fun getFilePolicy(
        existingFile: PsiFile?,
        targetFileName: String,
        targetDirectory: PsiDirectory
    ): ExistingFilePolicy {
        val message = KotlinBundle.message(
            "text.file.0.already.exists.in.1",
            targetFileName,
            targetDirectory.virtualFile.path
        )

        return if (existingFile !is KtFile) {
            if (isUnitTestMode()) return ExistingFilePolicy.OVERWRITE

            val answer = Messages.showOkCancelDialog(
                message,
                copyCommandName,
                KotlinBundle.message("action.text.overwrite"),
                KotlinBundle.message("action.text.cancel"),
                Messages.getQuestionIcon()
            )
            if (answer == Messages.OK) ExistingFilePolicy.OVERWRITE else ExistingFilePolicy.SKIP
        } else {
            if (isUnitTestMode()) return ExistingFilePolicy.APPEND

            val answer = Messages.showYesNoCancelDialog(
                message,
                copyCommandName,
                KotlinBundle.message("action.text.append"),
                KotlinBundle.message("action.text.overwrite"),
                KotlinBundle.message("action.text.cancel"),
                Messages.getQuestionIcon()
            )
            when (answer) {
                Messages.YES -> ExistingFilePolicy.APPEND
                Messages.NO -> ExistingFilePolicy.OVERWRITE
                else -> ExistingFilePolicy.SKIP
            }
        }
    }
}