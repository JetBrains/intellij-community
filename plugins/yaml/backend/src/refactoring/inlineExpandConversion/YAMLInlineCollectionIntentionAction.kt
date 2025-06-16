// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.refactoring.inlineExpandConversion

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import kotlinx.coroutines.launch
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.*
import org.jetbrains.yaml.psi.impl.YAMLArrayImpl
import org.jetbrains.yaml.psi.impl.YAMLHashImpl
import org.jetbrains.yaml.psi.impl.YAMLQuotedTextImpl

class YAMLInlineCollectionIntentionAction : PsiElementBaseIntentionAction(), LowPriorityAction {
  override fun startInWriteAction(): Boolean = false

  override fun getText(): String = YAMLBundle.message("yaml.intention.name.inline.collection")

  override fun getFamilyName(): String = text

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val element: PsiElement = getElement(editor, psiFile) ?: return IntentionPreviewInfo.EMPTY
    var collectionPointer: SmartPsiElementPointer<PsiElement>? = null
    var expandedElement: PsiElement? = null
    runBlockingCancellable {
      launch {
        collectionPointer = readAction { getYamlCollectionUnderCaret(element) } ?: return@launch
        val reformatted: SmartPsiElementPointer<PsiElement> = readAction { invokeInlineActionVirtually(collectionPointer!!) }
        expandedElement = reformatted.element
      }
    }

    val replaced: PsiElement = collectionPointer?.element?.replace(expandedElement ?: return IntentionPreviewInfo.EMPTY)
                               ?: return IntentionPreviewInfo.EMPTY
    removeExtraEolIfGivenElementIsKeyValue(replaced.parent)
    return IntentionPreviewInfo.DIFF
  }

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
    val collection = getYamlCollectionUnderCaret(element)?.element ?: return false
    return elementIsAvailableForInlining(collection)
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, editor.document)) return

    runWithModalProgressBlocking(project, YAMLBundle.message("yaml.progress.title.inlining.collection")) {
      val collectionPointer: SmartPsiElementPointer<PsiElement> = readAction { getYamlCollectionUnderCaret(element) }
                                                                  ?: return@runWithModalProgressBlocking
      val reformatted: SmartPsiElementPointer<PsiElement> = readAction { invokeInlineActionVirtually(collectionPointer) }
      executeWriteAction {
        val collection = collectionPointer.element!!
        val replaced = collection.replace(reformatted.element!!)
        removeExtraEolIfGivenElementIsKeyValue(replaced.parent)
      }
    }
  }

  /**
   * This method require PSI subtree of element to be correct JSON document
   */
  private fun invokeInlineActionVirtually(collectionPointer: SmartPsiElementPointer<PsiElement>): SmartPsiElementPointer<PsiElement> {
    val collectionCopy = collectionPointer.element!!.copy()
    var formatted = visitElementRecursiveAndInline(collectionCopy)
    formatted = CodeStyleManager.getInstance(formatted.project).reformat(formatted)
    val smartPointerManager = SmartPointerManager.getInstance(formatted.project)
    return smartPointerManager.createSmartPsiElementPointer(formatted)
  }


  private fun visitElementRecursiveAndInline(element: PsiElement): PsiElement {
    element.firstChild?.siblings()?.forEach { visitElementRecursiveAndInline(it) }
    if (elementIsAvailableForInlining(element)) return inlineElement(element, element.parent)
    return element
  }

  private fun inlineElement(element: PsiElement, keyValueParent: PsiElement): PsiElement {
    val formatted: PsiElement
    when (element) {
      is YAMLSequence -> {
        check(element !is YAMLArrayImpl)
        removeExtraEolIfGivenElementIsKeyValue(keyValueParent)
        formatted = sequenceToArray(element)
      }
      is YAMLMapping -> {
        check(element !is YAMLHashImpl)
        removeExtraEolIfGivenElementIsKeyValue(keyValueParent)
        formatted = mappingToHash(element)
      }
      else -> throw IllegalArgumentException("Unexpected attempt to inline element of type ${element.javaClass.name}")
    }
    return element.replace(formatted)
  }

  private fun removeExtraEolIfGivenElementIsKeyValue(keyValue: PsiElement) {
    if (keyValue !is YAMLKeyValue) return
    keyValue.firstChild.siblings().toList().reversed().forEach {
      if (it.elementType == YAMLTokenTypes.EOL || it.elementType == YAMLTokenTypes.COMMENT) {
        it.delete()
      }
    }
  }

  private fun safelyStringQuotedTextValue(text: PsiElement): PsiElement {
    if (text !is YAMLScalar) return text
    val factory = YAMLElementGenerator.getInstance(text.project)
    if (text is YAMLQuotedText) return text
    if (!text.textValue.contains("[\\s,]".toRegex())) return text
    return PsiTreeUtil.collectElementsOfType(factory.createDummyYamlWithText("\"${text.text}\""), YAMLQuotedTextImpl::class.java).iterator().next()
  }

  private fun sequenceToArray(sequence: YAMLSequence): YAMLArrayImpl {
    val sequenceItems = sequence.items
    val factory = YAMLElementGenerator.getInstance(sequence.project)
    val templateArray = factory.createDummyYamlWithText("[${List(sequenceItems.size) { "item$it" }.joinToString(", ")}]")
    val newArray = templateArray.firstChild.firstChild as YAMLArrayImpl
    for ((placeHolder, element) in newArray.items.zip(sequenceItems)) {
      placeHolder.lastChild.replace(safelyStringQuotedTextValue(element.lastChild))
    }
    return newArray
  }

  private fun mappingToHash(mapping: YAMLMapping): YAMLHashImpl {
    val mappingItems = mapping.keyValues
    val factory = YAMLElementGenerator.getInstance(mapping.project)
    val templateMappingHash = factory.createDummyYamlWithText("{${List(mappingItems.size) { "key$it: value$it" }.joinToString(", ")}}")
    val newMappingHash = templateMappingHash.firstChild.firstChild as YAMLHashImpl
    for ((placeHolder, element) in newMappingHash.keyValues.zip(mappingItems)) {
      placeHolder.key!!.replace(element.key!!)
      placeHolder.value!!.replace(safelyStringQuotedTextValue(element.value ?: factory.createSpace()))
    }
    return newMappingHash
  }

  private fun elementIsAvailableForInlining(element: PsiElement): Boolean {
    return when (element) {
      is YAMLMapping -> element !is YAMLHashImpl
      is YAMLSequence -> element !is YAMLArrayImpl
      else -> false
    }
  }
}

internal suspend fun <T> executeWriteAction(action: () -> T?): T? {
  return edtWriteAction {
    var x: T? = null
    executeCommand { x = action() }
    return@edtWriteAction x
  }
}
