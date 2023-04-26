// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule

class AssertKotlinFileInSpecificRootCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
    companion object {
        const val PREFIX: @NonNls String = CMD_PREFIX + "assertOpenedKotlinFileInRoot"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        withContext(Dispatchers.EDT) {
            val project = context.project
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor == null) {
                throw IllegalStateException("Selected editor is null")
            }
            val file = PsiDocumentManager.getInstance(context.project).getPsiFile(editor.document)
            if (file == null) {
                throw IllegalStateException("Psi file of document is null")
            }
            if (file.getKtModule() !is KtSourceModule) {
                throw IllegalStateException("File $file not in kt source root module")
            }
        }
    }
}