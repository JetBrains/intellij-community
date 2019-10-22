package com.intellij.codeInsight.completion

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

private const val DUMMY_IDENTIFIER = CompletionUtilCore.DUMMY_IDENTIFIER

data class FileWithOffsets(val file: PsiFile, val offsetMap: OffsetMap)

object CompletionUtils {
  fun insertDummyIdentifier(file: PsiFile, editor: Editor, offsetMap: OffsetMap): FileWithOffsets {
    val copy = file.copy() as PsiFile
    val copyDocument = copy.viewProvider.document
    requireNotNull(copyDocument)
    copyDocument.setText(editor.document.text)

    val startOffset = offsetMap.getOffset(CompletionInitializationContext.START_OFFSET)
    val endOffset = offsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET)

    val copyOffsets = offsetMap.copyOffsets(copyDocument)
    copyDocument.replaceString(startOffset, endOffset, DUMMY_IDENTIFIER)

    return FileWithOffsets(copy, copyOffsets)
  }

  fun createCompletionParameters(fileWithOffsets: FileWithOffsets, editor: Editor): CompletionParameters {
    val offset = fileWithOffsets.offsetMap.getOffset(CompletionInitializationContext.START_OFFSET)
    val originalFile = fileWithOffsets.file.originalFile
    var insertedElement = fileWithOffsets.file.findElementAt(offset)
    if (insertedElement == null && fileWithOffsets.file.textLength == offset) {
      insertedElement = PsiTreeUtil.getDeepestLast(fileWithOffsets.file)
    }
    requireNotNull(insertedElement)
    return CompletionParameters(insertedElement, originalFile, CompletionType.BASIC, offset, 1, editor, SimpleCompletionProcess.INSTANCE)
  }
}