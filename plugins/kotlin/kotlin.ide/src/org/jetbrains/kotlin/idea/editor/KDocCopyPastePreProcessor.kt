// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtFile

class KDocCopyPastePreProcessor : CopyPastePreProcessor {
    override fun preprocessOnCopy(file: PsiFile, startOffsets: IntArray, endOffsets: IntArray, text: String) = null

    override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText?): String {
        if (file !is KtFile) return text

        val offset = editor.selectionModel.selectionStart
        val element = file.findElementAt(offset)
        element?.parentOfType<KDoc>() ?: return text
        if (DocumentUtil.isAtLineEnd(offset, editor.document) &&
            text.startsWith("\n") &&
            text.firstOrNull { it !in " \n\t" } == '*'
        ) return text

        val document = editor.document
        val lineStartOffset = DocumentUtil.getLineStartOffset(offset, document)
        val chars = document.immutableCharSequence
        val firstNonWsLineOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t")
        if (firstNonWsLineOffset >= offset || chars[firstNonWsLineOffset] != '*') return text

        val lineStartReplacement = "\n" + chars.subSequence(lineStartOffset, firstNonWsLineOffset + 1) + " "
        return text.replace("\n", lineStartReplacement)
    }
}
