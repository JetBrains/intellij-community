// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.refactoring.inlineExpandConversion

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.DIFF
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.EMPTY
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.findTopmostParentInFile
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import kotlinx.coroutines.launch
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.impl.YAMLArrayImpl
import org.jetbrains.yaml.psi.impl.YAMLHashImpl
import org.jetbrains.yaml.psi.impl.YAMLSequenceImpl

private fun isSequenceOrMapping(element: PsiElement?): Boolean {
  return element is YAMLSequence || element is YAMLMapping
}

internal fun getYamlCollectionUnderCaret(element: PsiElement?): SmartPsiElementPointer<PsiElement>? {
  val smartPointerManager = SmartPointerManager.getInstance(element?.project ?: return null)
  if (isSequenceOrMapping(element)) return smartPointerManager.createSmartPsiElementPointer(element)
  val parent = element.parent ?: return null
  if (isSequenceOrMapping(parent)) return smartPointerManager.createSmartPsiElementPointer(parent)
  if (parent is YAMLKeyValue) {
    val lastChild = parent.getLastChild()
    if (isSequenceOrMapping(lastChild)) {
      return smartPointerManager.createSmartPsiElementPointer(lastChild)
    }
  }
  if ((parent is YAMLKeyValue || parent is YAMLSequenceItem) && isSequenceOrMapping(
      parent.parent)) return smartPointerManager.createSmartPsiElementPointer(parent.parent)
  return null
}


internal open class YAMLExpandCollectionIntentionAction : PsiElementBaseIntentionAction() {
  override fun startInWriteAction(): Boolean = false

  override fun getText(): String {
    return YAMLBundle.message("yaml.intention.name.expand.collection")
  }

  override fun getFamilyName(): String = text


  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val element: PsiElement = getElement(editor, psiFile) ?: return EMPTY
    var collectionPointer: SmartPsiElementPointer<PsiElement>? = null
    var expandedElement: PsiElement? = null
    runBlockingCancellable {
      launch {
            collectionPointer = readAction { getYamlCollectionUnderCaret(element) } ?: return@launch
            val expanded: SmartPsiElementPointer<PsiElement> = processParentsVirtually(collectionPointer) ?: return@launch
            expandedElement = expanded.element
          }
    }
    collectionPointer?.element!!
      .findTopmostParentInFile(true) { elementIsAvailableForExpanding(it) }
        ?.replace(expandedElement?: return EMPTY) ?: return EMPTY
    return DIFF
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, editor.document)) return

    runWithModalProgressBlocking(project, YAMLBundle.message("yaml.progress.title.expanding.yaml.collection")) {
      val collection: SmartPsiElementPointer<PsiElement> = readAction { getYamlCollectionUnderCaret(element) }
                                                           ?: return@runWithModalProgressBlocking
      val expanded: SmartPsiElementPointer<PsiElement> = processParentsVirtually(collection) ?: return@runWithModalProgressBlocking
      executeWriteAction {
        val topParent = collection.element!!.findTopmostParentInFile(true) { elementIsAvailableForExpanding(it) }
        topParent!!.replace(expanded.element!!)
      }
    }
  }

  private suspend fun processParentsVirtually(elementPointer: SmartPsiElementPointer<PsiElement>): SmartPsiElementPointer<PsiElement>? {
    return readAction {
      val element: PsiElement = elementPointer.element!!
      val project = element.project
      val smartPointerManager = SmartPointerManager.getInstance(project)
      val fileCopy = element.containingFile.copy()
      val elementUnderCaret = fileCopy.findElementAt(element.textOffset) ?: return@readAction null
      val currentElementCopy = getYamlCollectionUnderCaret(elementUnderCaret)?.element ?: return@readAction null
      val parents = getAllParentsForExpanding(currentElementCopy)
      val expandedTopParent = expandParents(parents) ?: return@readAction null
      return@readAction smartPointerManager.createSmartPsiElementPointer(expandedTopParent)
    }
  }

  private fun expandParents(parents: List<PsiElement>): PsiElement? = parents.map { expandElement(it) }.lastOrNull()

  private fun getAllParentsForExpanding(element: PsiElement): List<PsiElement> {
    return element.parents(true).filter {
      elementIsAvailableForExpanding(it)
    }.toList()
  }

  protected fun expandElement(element: PsiElement): PsiElement {
    val expanded = when (element) {
      is YAMLArrayImpl -> arrayToSequence(element)
      is YAMLHashImpl -> hashToMapping(element)
      else -> throw IllegalArgumentException("Unexpected attempt to expand element of type ${element.javaClass.name}")
    }
    return element.replace(expanded)
  }

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
    val collection = getYamlCollectionUnderCaret(element)?.element ?: return false
    return elementIsAvailableForExpanding(collection)
  }

  protected fun elementIsAvailableForExpanding(element: PsiElement): Boolean {
    return when (element) {
      is YAMLHashImpl -> element.keyValues.isNotEmpty()
      is YAMLArrayImpl -> element.items.isNotEmpty()
      else -> false
    }
  }

  private fun arrayToSequence(array: YAMLArrayImpl): YAMLSequence {
    val arrayItems = array.items
    val factory = YAMLElementGenerator.getInstance(array.project)
    val templateSequence = factory.createDummyYamlWithText(List(arrayItems.size) { "- item$it" }.joinToString("\n"))
    val newSequence = templateSequence.firstChild.firstChild as YAMLSequenceImpl
    for ((placeHolder, element) in newSequence.items.zip(arrayItems)) {
      placeHolder.lastChild.replace(element.lastChild)
    }
    return newSequence
  }

  private fun hashToMapping(mappingHash: YAMLHashImpl): YAMLMapping {
    val mappingHashItems = mappingHash.keyValues
    val factory = YAMLElementGenerator.getInstance(mappingHash.project)
    val templateMappingHash = factory.createDummyYamlWithText(List(mappingHashItems.size) { "key$it: value$it" }.joinToString("\n"))
    val newMapping = templateMappingHash.firstChild.firstChild as YAMLMapping
    for ((placeHolder, element) in newMapping.keyValues.zip(mappingHashItems)) {
      placeHolder.key!!.replace(element.key!!)
      placeHolder.value!!.replace(element.value ?: factory.createSpace())
    }
    return newMapping
  }
}

private class YAMLExpandAllCollectionsInsideIntentionAction : YAMLExpandCollectionIntentionAction() {
  override fun startInWriteAction(): Boolean = false

  override fun getText(): String = YAMLBundle.message("yaml.intention.name.expand.all.collections.inside")

  override fun getFamilyName(): String = text

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val element: PsiElement = getElement(editor, psiFile) ?: return EMPTY
    var collectionPointer: SmartPsiElementPointer<PsiElement>? = null
    var expandedElement: PsiElement? = null
    runBlockingCancellable {
      launch {
        collectionPointer = readAction { getYamlCollectionUnderCaret(element) ?: return@readAction null } ?: return@launch
        val processed = processChildrenVirtually(collectionPointer)
        expandedElement = processed.element
      }
    }
    collectionPointer!!.element?.replace(expandedElement ?: return EMPTY) ?: return EMPTY
    return DIFF
  }

  override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
    val collection = getYamlCollectionUnderCaret(element)?.element ?: return false
    return collection.findTopmostParentInFile(true) { elementIsAvailableForExpanding(it) } == collection
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, editor.document)) return
    runWithModalProgressBlocking(project, YAMLBundle.message("yaml.progress.title.expanding.yaml.collection")) {
      val collection: SmartPsiElementPointer<PsiElement> = readAction {
        getYamlCollectionUnderCaret(element) ?: return@readAction null
      } ?: return@runWithModalProgressBlocking
      val processed = processChildrenVirtually(collection)
      executeWriteAction { collection.element!!.replace(processed.element!!) }
    }
  }

  private suspend fun processChildrenVirtually(elementPointer: SmartPsiElementPointer<PsiElement>): SmartPsiElementPointer<PsiElement> {
    return readAction { expandElementRecursive(elementPointer.element!!.copy()) }
  }

  private fun expandElementRecursive(collection_: PsiElement): SmartPsiElementPointer<PsiElement> {
    var collection: PsiElement = collection_
    if (elementIsAvailableForExpanding(collection)) collection = expandElement(collection)

    for (child in collection.firstChild?.siblings()?.toList() ?: listOf())
      expandElementRecursive(child)
    val smartPointerManager = SmartPointerManager.getInstance(collection.project)
    return smartPointerManager.createSmartPsiElementPointer(collection)
  }
}