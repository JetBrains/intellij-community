package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.MockEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import kotlin.math.max

class BaseCompletionHandler(private val myProject: Project) {

  lateinit var editor: Editor
    private set

  private var myFileWithOffsets: FileWithOffsets? = null

  fun prepareCompletionParameters(file: PsiFile, offset: Int): CompletionParameters {
    val document = file.viewProvider.document
    requireNotNull(document)

    editor = MockEditor(file.virtualFile)
    editor.caretModel.moveToOffset(offset)

    val offsetMap = OffsetMap(document)
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, offset)
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, offset)
    CommandProcessor.getInstance().executeCommand(myProject, {
      val newOffsets = CompletionUtils.insertDummyIdentifier(file, editor, offsetMap)
      myFileWithOffsets = newOffsets
    }, "InsertDummyIdentifier", "")

    return CompletionUtils.createCompletionParameters(myFileWithOffsets!!, editor)
  }

  fun getLookupElements(params: CompletionParameters): List<LookupElement> {
    val lookupElements = mutableListOf<LookupElement>()
    CompletionService.getCompletionService().performCompletion(params) {
      lookupElements.add(it.lookupElement)
    }
    return lookupElements
  }

  fun complete(file: PsiFile, offset: Int) {
    val params = prepareCompletionParameters(file, offset)
    val lookupElements = getLookupElements(params)
    if (lookupElements.isEmpty()) return
    val replace = lookupElements[0]

    val editor = params.editor
    val position = params.position
    val prefix = if (position is LeafPsiElement) {
      val startOffset = position.startOffset
      position.text.substring(0, offset - startOffset)
    } else position.text

    val startOffset = max(0, offset - prefix.length)

    val document = editor.document
    CommandProcessor.getInstance().executeCommand(myProject, {
      document.deleteString(startOffset, offset)
      document.insertString(startOffset, replace.lookupString)
      editor.caretModel.moveToOffset(startOffset + replace.lookupString.length)
      val newOffsetMap = OffsetMap(document)
      newOffsetMap.addOffset(CompletionInitializationContext.START_OFFSET, editor.caretModel.offset)
      val context = InsertionContext(newOffsetMap, Lookup.AUTO_INSERT_SELECT_CHAR, lookupElements.toTypedArray(),
                                     file, editor, true)
      replace.handleInsert(context)
    }, "Completion", "")

    VfsUtil.saveText(file.virtualFile, document.text)
  }
}