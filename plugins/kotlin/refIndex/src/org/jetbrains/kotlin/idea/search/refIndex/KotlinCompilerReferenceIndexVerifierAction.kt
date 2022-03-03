// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")

package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.codeUsageScope
import org.jetbrains.kotlin.idea.search.toHumanReadableString
import org.jetbrains.kotlin.idea.search.useScope
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinCompilerReferenceIndexVerifierAction : AnAction(
    KotlinReferenceIndexBundle.lazyMessage("action.KotlinCompilerReferenceIndexVerifierAction.text")
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: kotlin.run {
            showProjectNotFound()
            return
        }

        val element = e.getData(CommonDataKeys.PSI_ELEMENT) ?: kotlin.run {
            showPsiElementNotFound(project)
            return
        }

        val pointToElement = element.createSmartPointer()
        ReadAction.nonBlocking<CompilerReferenceData?> {
            val psiElement = pointToElement.element ?: return@nonBlocking null
            val codeUsageScope = psiElement.codeUsageScope()
            val useScope = psiElement.useScope()

            CompilerReferenceData(
                elementText = "${psiElement::class.simpleName}:${psiElement.safeAs<PsiNamedElement>()?.name}",
                codeUsageScopeText = codeUsageScope.toHumanReadableString(),
                numberOfKotlinFilesInCodeUsageScope = codeUsageScope.countOfFileType(KotlinFileType.INSTANCE),
                numberOfJavaFilesInCodeUsageScope = codeUsageScope.countOfFileType(JavaFileType.INSTANCE),
                numberOfKotlinFilesInUseScope = useScope.countOfFileType(KotlinFileType.INSTANCE),
                numberOfJavaFilesInUseScope = useScope.countOfFileType(JavaFileType.INSTANCE),
            )
        }
            .finishOnUiThread(ModalityState.current()) { context ->
                if (context == null) return@finishOnUiThread
                dialog(
                    title = KotlinReferenceIndexBundle.message("dialog.title.compiler.index.status"),
                    project = project,
                    resizable = true,
                    panel = panel {
                        rowWithImmutableTextField(
                            label = "Element class:",
                            text = context.elementText
                        )

                        titledRow(title = "PsiSearchHelper#getCodeUsageScope") {
                            row {
                                immutableScrollableTextArea(text = context.codeUsageScopeText)
                            }
                        }

                        titledRow("Use Scope vs. Code Usage Scope") {
                            rowWithImmutableTextField(
                                label = "Number of Kotlin files:",
                                text = context.numberOfKotlinFilesInUseScope + "/" + context.numberOfKotlinFilesInCodeUsageScope
                            )
                            rowWithImmutableTextField(
                                label = "Number of Java files:",
                                text = context.numberOfJavaFilesInUseScope + "/" + context.numberOfJavaFilesInCodeUsageScope
                            )
                        }
                    }
                ).show()
            }
            .coalesceBy(this)
            .inSmartMode(project)
            .expireWhen { pointToElement.element == null || project.isDisposed() }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    companion object {
        private class CompilerReferenceData(
            val elementText: String,
            val codeUsageScopeText: String,
            val numberOfKotlinFilesInCodeUsageScope: String,
            val numberOfJavaFilesInCodeUsageScope: String,
            val numberOfKotlinFilesInUseScope: String,
            val numberOfJavaFilesInUseScope: String,
        )

        private fun SearchScope.countOfFileType(fileType: FileType): String = safeAs<GlobalSearchScope>()?.let {
            FileTypeIndex.getFiles(fileType, it).size.toString()
        } ?: "Non global scope"
    }
}

private fun RowBuilder.rowWithImmutableTextField(label: String, text: String) {
    row(label) {
        immutableTextField(text)
    }
}

private fun Row.immutableTextField(text: String) {
    textField(getter = { text }, setter = {}).applyToComponent { isEditable = false }
}

private fun Row.immutableScrollableTextArea(text: String) {
    scrollableTextArea(getter = { text }, setter = {}).applyToComponent { isEditable = false }
}

private fun showPsiElementNotFound(project: Project): Unit = showInvalidData(
    project,
    KotlinReferenceIndexBundle.message("dialog.message.psielement.not.found"),
)

private fun showProjectNotFound(): Unit = showInvalidData(
    null,
    KotlinReferenceIndexBundle.message("dialog.message.project.not.found"),
)

private fun showInvalidData(project: Project?, @NlsContexts.DialogMessage text: String): Unit = Messages.showErrorDialog(
    project,
    text,
    KotlinReferenceIndexBundle.message("dialog.title.invalid.data"),
)
