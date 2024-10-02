// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvmDecompiler

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.internal.KotlinJvmDecompilerFacade
import org.jetbrains.kotlin.psi.KtFile

class DecompileKotlinToJavaAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val binaryFile = getBinaryKotlinFile(e) ?: return

        KotlinJvmDecompilerFacade.getInstance()?.showDecompiledCode(binaryFile)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        when {
            KotlinPlatformUtils.isCidr -> e.presentation.isEnabledAndVisible = false
            else -> e.presentation.isEnabled = getBinaryKotlinFile(e) != null
        }
    }

    private fun getBinaryKotlinFile(e: AnActionEvent): KtFile? {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
        if (!file.canBeDecompiledToJava()) return null

        return file
    }

}

fun KtFile.canBeDecompiledToJava() = isCompiled && virtualFile?.fileType == JavaClassFileType.INSTANCE

// Add action to "Attach sources" notification panel
internal class DecompileKotlinToJavaActionProvider : AttachSourcesProvider {

    override fun getActions(
        orderEntries: List<LibraryOrderEntry>,
        psiFile: PsiFile
    ): Collection<AttachSourcesProvider.AttachSourcesAction> {
        if (psiFile !is KtFile || !psiFile.canBeDecompiledToJava()) return emptyList()

        return listOf(object : AttachSourcesProvider.LightAttachSourcesAction {
            override fun getName() = KotlinJvmDecompilerBundle.message("action.DecompileKotlinToJava.text")

            override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry>): ActionCallback {
                KotlinJvmDecompilerFacade.getInstance()?.showDecompiledCode(psiFile)
                return ActionCallback.DONE
            }

            override fun getBusyText() = KotlinJvmDecompilerBundle.message("action.decompile.busy.text")
        })
    }
}
